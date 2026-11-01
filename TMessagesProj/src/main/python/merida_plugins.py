"""
MeridaGram plugin runtime (stage 1).

A plugin is a .plugin file with Python code. MeridaGram reads its metadata
(__id__, __name__, __description__, __author__, __version__) without running it,
and runs it only when plugins are on in MeridaGram Settings → Plugins and the
plugin itself is switched on.

Hooks a plugin can define (all optional):
    on_plugin_load()                 called after the plugin is loaded
    on_plugin_unload()               called before it is switched off or removed
    on_send_message(text, dialog_id) called before a text message is sent;
                                     return a new text to replace it, None to keep it,
                                     or "" to not send the message at all.
Plugins have full access to Python and, through `from java import jclass`, to MeridaGram.
"""

import importlib.machinery
import importlib.util
import inspect
import sys
import traceback

sys.dont_write_bytecode = True

_loaded = {}  # plugin id -> module


def _module_name(plugin_id):
    return "merida_plugin_" + plugin_id


def load(plugin_id, path):
    """Loads (or reloads) a plugin. Returns "" on success, otherwise the error text."""
    unload(plugin_id)
    name = _module_name(plugin_id)
    try:
        loader = importlib.machinery.SourceFileLoader(name, path)
        spec = importlib.util.spec_from_loader(name, loader)
        module = importlib.util.module_from_spec(spec)
        sys.modules[name] = module
        loader.exec_module(module)
        hook = getattr(module, "on_plugin_load", None)
        if callable(hook):
            hook()
        _loaded[plugin_id] = module
        return ""
    except BaseException:
        sys.modules.pop(name, None)
        return traceback.format_exc(limit=4)


def unload(plugin_id):
    module = _loaded.pop(plugin_id, None)
    if module is None:
        return
    hook = getattr(module, "on_plugin_unload", None)
    if callable(hook):
        try:
            hook()
        except BaseException:
            traceback.print_exc()
    sys.modules.pop(_module_name(plugin_id), None)


def unload_all():
    for plugin_id in list(_loaded):
        unload(plugin_id)


def has_send_hooks():
    return any(callable(getattr(m, "on_send_message", None)) for m in _loaded.values())


def _call(fn, *args):
    """Calls a hook with as many arguments as it accepts, so on_send_message(text) also works."""
    try:
        params = inspect.signature(fn).parameters.values()
        if not any(p.kind == p.VAR_POSITIONAL for p in params):
            count = sum(1 for p in params if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD))
            args = args[:count]
    except (TypeError, ValueError):
        pass
    return fn(*args)


def on_send_message(text, dialog_id):
    """Passes the text through every plugin. Returns the final text, or "" to not send."""
    for plugin_id, module in list(_loaded.items()):
        hook = getattr(module, "on_send_message", None)
        if not callable(hook):
            continue
        try:
            result = _call(hook, text, dialog_id)
        except BaseException:
            traceback.print_exc()
            continue
        if result is None:
            continue
        text = str(result)
        if text == "":
            return ""
    return text
