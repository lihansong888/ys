#!/usr/bin/env python3
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "smali")
target = root / "com/github/catvod/spider/Init.smali"
if not target.is_file():
    raise SystemExit("ERROR: expected Init.smali was not found")
text = target.read_text(encoding="utf-8")
pattern = re.compile(r"(?ms)^\.method[^\n]*\binit\(Landroid/content/Context;\)V\n.*?^\.end method")
matches = list(pattern.finditer(text))
if len(matches) != 1:
    raise SystemExit("ERROR: expected exactly one Init.init(Context) method; found %d" % len(matches))
block = matches[0].group(0)
call = "invoke-static {p0}, Lcom/example/popup/GateHook;->run(Landroid/content/Context;)V"
if call in block:
    raise SystemExit("ERROR: hook call already exists; refusing to insert twice")
returns = list(re.finditer(r"(?m)^\s*return-void\s*$", block))
if len(returns) != 1:
    raise SystemExit("ERROR: expected exactly one return-void in Init.init(Context); found %d" % len(returns))
pos = returns[0].start()
block = block[:pos] + "    " + call + "\n" + block[pos:]
text = text[:matches[0].start()] + block + text[matches[0].end():]
target.write_text(text, encoding="utf-8")
print("Injected announcement hook into Init.init(Context)")
