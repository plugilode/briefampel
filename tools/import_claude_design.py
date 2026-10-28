"""Preserve the supplied Claude originals and package their presentation, without redrawing it."""
from pathlib import Path
import hashlib
import json
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(r"C:\Users\Shadow\AppData\Local\Temp\claude\C--Users-Shadow-ampel\1d75cc47-62cb-4fd8-a0fc-d5d232a97aca\scratchpad\preview")
ARCHIVE = ROOT / "docs" / "claude-original"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "briefampel"
ARCHIVE.mkdir(parents=True, exist_ok=True)
(ASSETS / "fonts").mkdir(parents=True, exist_ok=True)
templates = {}
manifest = {}
for source in sorted(SOURCE.glob("*.html")):
    raw = source.read_bytes()
    shutil.copy2(source, ARCHIVE / source.name)
    html = raw.decode("utf-8-sig")
    dimensions = re.search(r"width: 390px; height: (\d+)px", html)
    templates[source.stem] = {"html": html, "height": int(dimensions[1])}
    manifest[source.name] = {"sha256": hashlib.sha256(raw).hexdigest(), "width": 390, "height": int(dimensions[1])}
for source in (ROOT / "app/src/main/res/font").glob("*.ttf"):
    shutil.copy2(source, ASSETS / "fonts" / source.name)
(ASSETS / "templates.json").write_text(json.dumps(templates, ensure_ascii=False), encoding="utf-8")
(ARCHIVE / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
for filename in ("Screenshot 2026-10-26 230852.png", "Screenshot 2026-10-26 230916.png", "Screenshot 2026-10-26 230929.png"):
    shutil.copy2(Path(r"C:\Users\Shadow\Pictures\Screenshots") / filename, ARCHIVE / filename)
print(f"Archived {len(templates)} unmodified Claude originals and packaged offline fonts/templates.")
