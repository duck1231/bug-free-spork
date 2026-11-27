# RoamCast för GrapheneOS

Ombyggd version av RoamCast (`se.roamcast.irl`) som fungerar bättre på GrapheneOS
och med fler streamingtjänster.

**Färdig app:** [`RoamCast-GrapheneOS.apk`](RoamCast-GrapheneOS.apk)

## Vad som är ändrat

| Problem i originalet | Fix |
|---|---|
| Sändningen stoppades så fort skärmen släcktes eller du bytte app | En förgrundstjänst (notisen "RoamCast är LIVE 🔴") håller kamera, mikrofon, CPU och wifi igång. Sändningen och inspelningen fortsätter tills du trycker stopp. |
| Bara Twitch, Kick och YouTube fanns som färdiga val | Färdiga val för **Twitch, Kick, YouTube, Facebook, TikTok, Instagram, X (Twitter), Trovo, Rumble, DLive, Vimeo och Restream**, plus egen server, SRT och SRTLA |
| Stream keys med mellanslag eller radbrytningar, eller hela URL:en inklistrad i fel fält, gav fel | Stream key och URL städas automatiskt. Hela URL:en kan klistras in i antingen server- eller key-fältet. Kick får `/app`, och RTMPS får port 443 om den saknas. |
| Egen server krävde `rtmp://` | `rtmp://`, `rtmps://`, `rtsp://`, `srt://` och `udp://` fungerar |
| Byggd som debug-version | Debug-flaggan är borttagen, så appen går snabbare |

## Installera på GrapheneOS

1. **Avinstallera den gamla RoamCast först.** Den nya är signerad med en annan nyckel,
   så Android vägrar annars installera den. Kanaler och inställningar försvinner, så skriv upp dina stream keys innan.
2. Öppna `RoamCast-GrapheneOS.apk` på telefonen och tillåt installation från den appen du öppnar den med.
3. Ge behörigheterna **Kamera**, **Mikrofon** och **Plats** (för hastighet och sträcka på overlayn).
   Svara **Tillåt** på frågan om notiser när du går live första gången.
4. Under *Inställningar → Appar → RoamCast*:
   - **Nätverk**: på (GrapheneOS-specifik behörighet, utan den går inget att sända)
   - **Batteri → Obegränsad**: rekommenderas för långa IRL-sändningar
5. Google Play-tjänster behövs **inte**.

## Bygga om själv

```bash
./build.sh /sökväg/till/RoamCast.apk
```

Skriptet hämtar apktool, D8, android.jar och uber-apk-signer, patchar appen (`patch.py`),
kompilerar den nya koden i `java/` och signerar med uber-apk-signers inbyggda debug-nyckel.
Samma nyckel används vid varje bygge, så nya versioner kan installeras ovanpå utan att avinstallera.
