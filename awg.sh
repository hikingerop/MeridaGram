#!/bin/bash
# Туннель AmneziaWG между входным сервером (Москва) и главным (за границей).
# Россия режет TCP-соединения к зарубежному хостингу, а AmneziaWG идёт по UDP и маскирует пакеты.
#
# 1) На ГЛАВНОМ сервере (через веб-консоль хостинга):
#      curl -fsSL https://raw.githubusercontent.com/hikingerop/MeridaGram/main/awg.sh | bash -s -- main СЛОВО
# 2) На ВХОДНОМ сервере (Москва, через Termius):
#      curl -fsSL https://raw.githubusercontent.com/hikingerop/MeridaGram/main/awg.sh | bash -s -- relay СЛОВО IP_ГЛАВНОГО
#
# СЛОВО — любое одно слово из латинских букв и цифр, одинаковое на обоих серверах. Из него оба
# сервера сами получают ключи и настройки туннеля, поэтому длинные ключи вводить не нужно.
# Входной сервер переключит пересылку на туннель только если через него реально проходят данные.
set -euo pipefail

ROLE="${1:-}"
WORD="${2:-}"
MAIN_IP="${3:-}"
MAIN_TUN=10.77.0.1
RELAY_TUN=10.77.0.2
CONF_DIR=/etc/amnezia/amneziawg
CONF="$CONF_DIR/awg0.conf"

die() { echo "❌ $*"; exit 1; }

[ "$ROLE" = main ] || [ "$ROLE" = relay ] || die "Первым словом укажите main (главный сервер) или relay (входной)."
echo "$WORD" | grep -qE '^[A-Za-z0-9]{6,}$' || die "СЛОВО: только латинские буквы и цифры, не короче 6 символов."
if [ "$ROLE" = relay ]; then
  echo "$MAIN_IP" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' || die "Третьим укажите IP главного сервера, например 46.30.46.114"
fi
[ "$(id -u)" = 0 ] || die "Запустите от root."

# ---------- установка AmneziaWG ----------
if ! command -v awg > /dev/null || ! command -v awg-quick > /dev/null; then
  echo "→ Ставлю AmneziaWG (1–3 минуты)…"
  . /etc/os-release
  [ "${ID:-}" = ubuntu ] || die "Скрипт рассчитан на Ubuntu, а здесь ${PRETTY_NAME:-неизвестная система}. Пришлите это сообщение в чат."
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq software-properties-common python3-launchpadlib gnupg2 curl > /dev/null
  apt-get install -y -qq "linux-headers-$(uname -r)" > /dev/null 2>&1 || echo "  (заголовки ядра не нашлись — попробую без них)"
  add-apt-repository -y ppa:amnezia/ppa > /dev/null
  apt-get update -qq
  apt-get install -y -qq amneziawg > /dev/null || die "Не удалось поставить AmneziaWG. Пришлите скриншот этого экрана в чат."
fi
modprobe amneziawg 2> /dev/null || true

# ---------- ключи и настройки из СЛОВА (одинаковые на обоих серверах) ----------
read -r MAIN_PRIV RELAY_PRIV PORT S1 S2 H1 H2 H3 H4 < <(python3 - "$WORD" <<'PY'
import sys, hashlib, base64
w = sys.argv[1].encode()
h = lambda tag: hashlib.sha256(b"merida-tunnel:" + tag + b":" + w).digest()
key = lambda tag: base64.b64encode(h(tag)).decode()
n = lambda tag, lo, hi: lo + int.from_bytes(h(tag)[:4], "big") % (hi - lo)
s1 = n(b"s1", 20, 120)
s2 = n(b"s2", 20, 120)
if s1 + 56 == s2:
    s2 += 1
hs = []
for t in (b"h1", b"h2", b"h3", b"h4"):
    v = n(t, 5, 2147483647)
    while v in hs:
        v += 1
    hs.append(v)
print(key(b"main"), key(b"relay"), n(b"port", 30000, 60000), s1, s2, *hs)
PY
)
MAIN_PUB=$(echo "$MAIN_PRIV" | awg pubkey)
RELAY_PUB=$(echo "$RELAY_PRIV" | awg pubkey)
OBFS="Jc = 5
Jmin = 40
Jmax = 900
S1 = $S1
S2 = $S2
H1 = $H1
H2 = $H2
H3 = $H3
H4 = $H4"

mkdir -p "$CONF_DIR"
umask 077
if [ "$ROLE" = main ]; then
  cat > "$CONF" <<EOF
[Interface]
PrivateKey = $MAIN_PRIV
Address = $MAIN_TUN/30
ListenPort = $PORT
MTU = 1380
$OBFS

[Peer]
PublicKey = $RELAY_PUB
AllowedIPs = $RELAY_TUN/32
EOF
else
  cat > "$CONF" <<EOF
[Interface]
PrivateKey = $RELAY_PRIV
Address = $RELAY_TUN/30
MTU = 1380
$OBFS

[Peer]
PublicKey = $MAIN_PUB
Endpoint = $MAIN_IP:$PORT
AllowedIPs = $MAIN_TUN/32
PersistentKeepalive = 25
EOF
fi

echo "→ Запускаю туннель…"
systemctl enable -q awg-quick@awg0
systemctl restart awg-quick@awg0 || die "Туннель не запустился: $(systemctl status awg-quick@awg0 --no-pager -n 5 | tail -3)"

if [ "$ROLE" = main ]; then
  if command -v ufw > /dev/null && ufw status | grep -q "Status: active"; then
    ufw allow "$PORT/udp" > /dev/null
  fi
  # входной сервер теперь приходит с адреса туннеля — для пробных периодов это тот же входной сервер
  ENV=/opt/vpn-bot/.env
  if [ -f "$ENV" ] && ! grep -qE "^RELAY_IPS=.*$RELAY_TUN" "$ENV"; then
    if grep -qE '^RELAY_IPS=' "$ENV"; then
      sed -i -E "s/^(RELAY_IPS=.*[^,])\s*$/\1,$RELAY_TUN/; s/^RELAY_IPS=\s*$/RELAY_IPS=$RELAY_TUN/" "$ENV"
    else
      echo "RELAY_IPS=$RELAY_TUN" >> "$ENV"
    fi
    systemctl restart vpn-bot 2> /dev/null || true
  fi
  echo
  echo "✅ Главный сервер готов. Порт туннеля: $PORT/udp."
  echo "   Теперь выполните команду на входном сервере (Москва) с тем же словом и IP этого сервера."
  exit 0
fi

# ---------- входной сервер: проверка и переключение ----------
echo "→ Жду связи с главным сервером…"
OK=0
for i in $(seq 1 20); do
  if awg show awg0 latest-handshakes 2> /dev/null | awk '{print $2}' | grep -qvE '^0?$'; then OK=1; break; fi
  sleep 1
done
[ "$OK" = 1 ] || die "Главный сервер не отвечает по туннелю. Проверьте, что на нём выполнена команда с main и тем же словом. Ничего не переключено, всё работает как раньше."

echo "→ Проверяю, что через туннель проходят данные…"
CODE=$(curl -sk --max-time 15 -o /dev/null -w "%{http_code}" "https://$MAIN_TUN:8443/" || true)
if [ "$CODE" = "000" ] || [ -z "$CODE" ]; then
  die "Связь есть, но данные через туннель не идут. Ничего не переключено, всё работает как раньше. Пришлите скриншот в чат."
fi
echo "  Ответ сервера приложения через туннель: $CODE — данные проходят."

echo "→ Переключаю пересылку на туннель…"
curl -fsSL "https://hikingerop.github.io/dl/relay.sh?$(date +%s)" | CLAMP=1 bash -s -- "$MAIN_TUN" > /dev/null
echo
echo "✅ Готово: Москва пересылает VPN на главный сервер через туннель AmneziaWG."
echo "   Ключи у людей не меняются. Проверьте VPN на телефоне."
echo "   Вернуть как было: curl -fsSL https://hikingerop.github.io/dl/relay.sh | CLAMP=1 bash -s -- $MAIN_IP"
