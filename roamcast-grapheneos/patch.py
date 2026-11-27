#!/usr/bin/env python3
"""Patch an apktool-decoded RoamCast tree for GrapheneOS + more stream platforms.

Usage: patch.py <apktool-decoded-dir>
"""
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1])
IRL = next(p for p in ROOT.glob("smali*/se/roamcast/irl") if (p / "DestKind.smali").exists())

# name, label, default server, hint
PLATFORMS = [
    ("TWITCH", "Twitch", "rtmp://live.twitch.tv/app",
     "Stream key från Twitch Creator Dashboard → Inställningar → Stream"),
    ("KICK", "Kick", "rtmps://fa723fc1b171.global-contribute.live-video.net/app",
     "Stream URL + key från Kick → Settings → Stream"),
    ("YOUTUBE", "YouTube", "rtmp://a.rtmp.youtube.com/live2",
     "Stream key från YouTube Studio → Sänd live"),
    ("FACEBOOK", "Facebook", "rtmps://live-api-s.facebook.com:443/rtmp/",
     "Stream key från Facebook Live Producer → Streamingprogram"),
    ("TIKTOK", "TikTok", "rtmp://",
     "Server-URL + stream key från TikTok LIVE Studio / LIVE Center (byts varje sändning). Hela URL:en går också bra."),
    ("INSTAGRAM", "Instagram", "rtmps://",
     "Instagram → Live → Streamingprogram: klistra in URL + stream key (byts varje sändning)"),
    ("X", "X (Twitter)", "rtmps://va.pscp.tv:443/x",
     "Stream key från X Media Studio → Producer → Källor"),
    ("TROVO", "Trovo", "rtmp://livepush.trovo.live/live",
     "Stream key från Trovo Creator Studio → Stream"),
    ("RUMBLE", "Rumble", "rtmp://",
     "Server-URL + stream key från Rumble → Go Live. Hela URL:en går också bra."),
    ("DLIVE", "DLive", "rtmp://stream.dlive.tv/live",
     "Stream key från DLive → Dashboard → Stream setup"),
    ("VIMEO", "Vimeo", "rtmps://rtmp-global.cloud.vimeo.com:443/live",
     "Stream key från Vimeo → Event → RTMP-inställningar"),
    ("RESTREAM", "Restream (flera samtidigt)", "rtmp://live.restream.io/live",
     "Stream key från Restream → Streaming Setup. Skickar vidare till alla dina kanaler samtidigt."),
    ("RTMP", "Egen server (RTMP/RTMPS/RTSP)", "rtmp://",
     "Hela adressen, t.ex. rtmp://server/app/nyckel. rtmps://, rtsp:// och udp:// fungerar också."),
    ("SRT", "SRT (t.ex. OBS)", "srt://",
     "srt://ip:port — OBS: Media-källa med srt://0.0.0.0:9000?mode=listener"),
    ("SRTLA", "SRTLA bonding", "srtla://",
     "srtla://server:5000 — BELABOX cloud, egen srtla_rec eller IRLToolkit"),
]
OLD_ENUMS = {"TWITCH", "KICK", "YOUTUBE", "RTMP", "SRT", "SRTLA"}


def smali_str(s: str) -> str:
    out = []
    for ch in s:
        o = ord(ch)
        if ch == '"':
            out.append('\\"')
        elif ch == "\\":
            out.append("\\\\")
        elif 0x20 <= o < 0x7F:
            out.append(ch)
        elif o > 0xFFFF:
            o -= 0x10000
            out.append("\\u%04x\\u%04x" % (0xD800 + (o >> 10), 0xDC00 + (o & 0x3FF)))
        else:
            out.append("\\u%04x" % o)
    return '"' + "".join(out) + '"'


def replace_method(src: str, signature: str, body: str) -> str:
    pat = re.compile(r"^\.method [^\n]*" + re.escape(signature) + r"\n.*?^\.end method\n", re.S | re.M)
    new, n = pat.subn(lambda m: m.group(0).split("\n", 1)[0] + "\n" + body + ".end method\n", src)
    if n != 1:
        raise SystemExit(f"method {signature}: {n} matches")
    return new


def sub_once(src: str, pattern: str, repl: str, what: str, flags=0) -> str:
    new, n = re.subn(pattern, repl, src, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f"patch '{what}' did not apply")
    return new


# ---------------------------------------------------------------- DestKind
p = IRL / "DestKind.smali"
s = p.read_text()
fields = "".join(
    f".field public static final enum {n}:Lse/roamcast/irl/DestKind;\n\n"
    for n, *_ in PLATFORMS if n not in OLD_ENUMS)
s = sub_once(s, r"(\.field public static final enum KICK:Lse/roamcast/irl/DestKind;\n)",
             lambda m: fields + m.group(1), "enum fields")

clinit = ["    .locals 6\n"]
for i, (n, label, server, hint) in enumerate(PLATFORMS):
    clinit += [
        "    new-instance v0, Lse/roamcast/irl/DestKind;\n",
        f"    const-string v1, {smali_str(n)}\n",
        f"    const/16 v2, 0x{i:x}\n",
        f"    const-string v3, {smali_str(label)}\n",
        f"    const-string v4, {smali_str(server)}\n",
        f"    const-string v5, {smali_str(hint)}\n",
        "    invoke-direct/range {v0 .. v5}, Lse/roamcast/irl/DestKind;-><init>"
        "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V\n",
        f"    sput-object v0, Lse/roamcast/irl/DestKind;->{n}:Lse/roamcast/irl/DestKind;\n\n",
    ]
clinit += [
    "    invoke-static {}, Lse/roamcast/irl/DestKind;->$values()[Lse/roamcast/irl/DestKind;\n",
    "    move-result-object v0\n",
    "    sput-object v0, Lse/roamcast/irl/DestKind;->$VALUES:[Lse/roamcast/irl/DestKind;\n",
    "    check-cast v0, [Ljava/lang/Enum;\n",
    "    invoke-static {v0}, Lkotlin/enums/EnumEntriesKt;->enumEntries([Ljava/lang/Enum;)Lkotlin/enums/EnumEntries;\n",
    "    move-result-object v0\n",
    "    sput-object v0, Lse/roamcast/irl/DestKind;->$ENTRIES:Lkotlin/enums/EnumEntries;\n",
    "    return-void\n",
]
s = replace_method(s, "constructor <clinit>()V", "".join(clinit))

values = ["    .locals 3\n", f"    const/16 v0, 0x{len(PLATFORMS):x}\n",
          "    new-array v0, v0, [Lse/roamcast/irl/DestKind;\n"]
for i, (n, *_) in enumerate(PLATFORMS):
    values += [f"    const/16 v1, 0x{i:x}\n",
               f"    sget-object v2, Lse/roamcast/irl/DestKind;->{n}:Lse/roamcast/irl/DestKind;\n",
               "    aput-object v2, v0, v1\n"]
values.append("    return-object v0\n")
s = replace_method(s, "synthetic $values()[Lse/roamcast/irl/DestKind;", "".join(values))

# Every platform except SRT/SRTLA uses a stream key.
s = replace_method(s, "getUsesKey()Z",
                   "    .locals 1\n"
                   "    invoke-virtual {p0}, Lse/roamcast/irl/DestKind;->isSrt()Z\n"
                   "    move-result v0\n"
                   "    xor-int/lit8 v0, v0, 0x1\n"
                   "    return v0\n")
p.write_text(s)

# ---------------------------------------------------------------- Destination.rtmpUrl
p = IRL / "Destination.smali"
s = p.read_text()
s = replace_method(s, "rtmpUrl()Ljava/lang/String;",
                   "    .locals 3\n"
                   "    iget-object v0, p0, Lse/roamcast/irl/Destination;->kind:Lse/roamcast/irl/DestKind;\n"
                   "    iget-object v1, p0, Lse/roamcast/irl/Destination;->server:Ljava/lang/String;\n"
                   "    iget-object v2, p0, Lse/roamcast/irl/Destination;->key:Ljava/lang/String;\n"
                   "    invoke-static {v0, v1, v2}, Lse/roamcast/irl/gos/Fix;->buildUrl"
                   "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;\n"
                   "    move-result-object v0\n"
                   "    return-object v0\n")
p.write_text(s)

# ---------------------------------------------------------------- StreamEngine
p = IRL / "StreamEngine.smali"
s = p.read_text()
s = replace_method(s, "usesKeyMissing(Lse/roamcast/irl/Destination;)Z",
                   "    .locals 3\n"
                   "    invoke-virtual {p1}, Lse/roamcast/irl/Destination;->getKind()Lse/roamcast/irl/DestKind;\n"
                   "    move-result-object v0\n"
                   "    invoke-virtual {p1}, Lse/roamcast/irl/Destination;->getServer()Ljava/lang/String;\n"
                   "    move-result-object v1\n"
                   "    invoke-virtual {p1}, Lse/roamcast/irl/Destination;->getKey()Ljava/lang/String;\n"
                   "    move-result-object v2\n"
                   "    invoke-static {v0, v1, v2}, Lse/roamcast/irl/gos/Fix;->keyMissing"
                   "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Z\n"
                   "    move-result v0\n"
                   "    return v0\n")

# Accept rtmp/rtmps/rtsp/srt/udp instead of only "rtmp*".
s = sub_once(s,
             r'const-string v4, "rtmp"\n(\s*\.line \d+\n)?\s*invoke-static \{v1, v4, v0, v3, v2\}, '
             r'Lkotlin/text/StringsKt;->startsWith\$default\(Ljava/lang/String;Ljava/lang/String;ZILjava/lang/Object;\)Z',
             "invoke-static {v1}, Lse/roamcast/irl/gos/Fix;->isSupported(Ljava/lang/String;)Z",
             "protocol check")
s = sub_once(s, re.escape(smali_str("Adressen måste börja med rtmp:// eller rtmps://")),
             smali_str("Ogiltig adress — den ska börja med rtmp://, rtmps://, rtsp://, srt:// eller udp://")
             .replace("\\", "\\\\"),
             "protocol message")

# Start the foreground service as soon as we go live ...
s = sub_once(s,
             r"(\.method public final goLive\(.*?const/4 v0, 0x1\n(?:\s*\.line \d+\n)?"
             r"\s*iput-boolean v0, p0, Lse/roamcast/irl/StreamEngine;->wantLive:Z\n)",
             lambda m: m.group(1) +
             "\n    iget-object v1, p0, Lse/roamcast/irl/StreamEngine;->context:Landroid/content/Context;\n"
             "    invoke-static {v1}, Lse/roamcast/irl/gos/LiveService;->start(Landroid/content/Context;)V\n",
             "service start", re.S)
# ... and stop it with the stream.
s = sub_once(s,
             r"(\.method public final stop\(\)V\n\s*\.locals \d+\n)",
             lambda m: m.group(1) +
             "\n    iget-object v0, p0, Lse/roamcast/irl/StreamEngine;->context:Landroid/content/Context;\n"
             "    invoke-static {v0}, Lse/roamcast/irl/gos/LiveService;->stop(Landroid/content/Context;)V\n",
             "service stop")
p.write_text(s)

# ---------------------------------------------------------------- MainActivity.onStop
# Keep streaming (and recording) when the screen turns off or the app is left.
p = IRL / "MainActivity.smali"
s = p.read_text()
s = sub_once(s,
             r"(\.method protected onStop\(\)V.*?invoke-virtual \{v0\}, Lse/roamcast/irl/StreamEngine;->getWantLive\(\)Z\n"
             r"(?:\s*\.line \d+\n)?\s*move-result v0\n(?:\s*\.line \d+\n)?\s*if-eqz v0, :cond_\d+\n)",
             lambda m: m.group(1) + "\n    return-void\n",
             "onStop keep-alive", re.S)
p.write_text(s)

# ---------------------------------------------------------------- Manifest
p = ROOT / "AndroidManifest.xml"
s = p.read_text()
perms = ["FOREGROUND_SERVICE", "FOREGROUND_SERVICE_CAMERA", "FOREGROUND_SERVICE_MICROPHONE",
         "POST_NOTIFICATIONS", "WAKE_LOCK"]
add = "".join(f'<uses-permission android:name="android.permission.{x}"/>' for x in perms
              if f"android.permission.{x}\"" not in s)
s = sub_once(s, r'(<uses-permission android:name="android.permission.CAMERA"/>)',
             lambda m: m.group(1) + add, "permissions")
s = sub_once(s, r"(</application>)",
             lambda m: '<service android:exported="false" android:foregroundServiceType="camera|microphone" '
                       'android:name="se.roamcast.irl.gos.LiveService"/>' + m.group(1),
             "service")
# Release behaviour: debuggable builds run noticeably slower on ART.
s = s.replace(' android:debuggable="true"', "")
p.write_text(s)

# ---------------------------------------------------------------- version
p = ROOT / "apktool.yml"
s = p.read_text()
s = re.sub(r"(versionName: )'?([^'\n]*)'?", lambda m: f"{m.group(1)}{m.group(2)}-grapheneos", s, count=1)
p.write_text(s)

print("patched", IRL)
