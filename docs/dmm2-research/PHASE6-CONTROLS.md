# Reward Store: bronbewijs voor de resterende View/Help/filter-controls

17 november 2026, OpenRS2 cache 2686. Alle probes lezen uitsluitend de broncache.
`phase6-sources/controls.json` bewaart scripts 5771, 6388, 7152..7156, 1972 en 98 met de SHA256
van de originele payload; enum 4988 bevat ook de volledige bytes. De bestaande script-7142/7147-probes
staan in `phase5-sources/omnishop-scripts.json`. Geen donor, cachemutatie of nieuwe lokale asset-id.

De bronhint uit Claude's vensterronde 16:1x is hiermee nader bewezen:

- **819:11 = View**, niet de filterdropdown. Script 7153 gebruikt sprite 4735 en tekst Compact;
  bij varp 3871 = 1 gebruikt het sprite 4737 en tekst Expand. Het kind is 16x16 op 2,2,
  gemaakt via 98 -> 6388. Script 7155 wisselt de varp en bouwt de winkel opnieuw via 7142.
- **819:12 = Help**. Sprite 8386, kind 20x20 op 0,0. Desktop mouse-over/leave wisselt naar
  8387/8386 (scripts 229/1972); script 7154 wist de selectie en hertekent de Help-informatie.
- **819:13 = filterdropdown**. Script 5771 bouwt achtergrond 297, frame via 715, pijl 788
  rechts in het midden, 16x16, en een gecentreerde p11-label (font 494, shadow=true).
  De pijl heeft bij een lege enum alpha 125. Enum 4988 bevat 0 entries en default-string
  `No filters available`; de labelkleur is dan 10461087 = 0x9f9f9f. Er is in die tak geen
  Expand-op of filterkeuze. De normale actieve labelkleur is 16750623 = 0xff981f.
- Script 7142 houdt de rechter informatiekolom in de normale stand (varp 3871 = 0).
  Het maakt topbar 10 smaller met width=218, widthMode=1 en filter 13 met width=26,
  widthMode=1 wanneer de extra currencyheader niet wordt gebruikt. De basisdecode met
  topbar width=12 / filter width=200 is dus geen volledig uitgevoerde normal-view-layout.
  De stand 3871=1 gebruikt andere maten/zichtbaarheid en een modal Item Info/Help-route.

Het bronplaatje `phase5-sources/reward-store.png` toont de disabled dropdown plus de View-pijl
naast het normale productraster. Alleen de oorspronkelijke verborgen controls weer zichtbaar maken
zou lege controls en verkeerde maten opleveren. De huidige server/importeradapter bouwt daarom de
bronkinderen, het grijze dropdownframe, beide View-layouts en expanded Help/Item Info met eigen
steelframe. De bronbijlage bevat inmiddels veertien scripts plus enum4988, inclusief7142/43/44/47 en715.

## Gebouwd contract en Claude's cache-ronde

`Dmm2WindowPort` behoudt de oude transactiewidgets en voegt193..210 toe: View/Help/filter,
plus de source228-steelframe voor de grote Info/Help-weergave.819 heeft211componenten.
De normale productcards zijn111x74/tweekolommen; de andere stand gebruikt51x50/achtkolommen,
3pxgaps en verbergt de productnamen, berekend uit7144. View-caption en icon volgen7153:
portretstand `Compact`/4735; kleinekaartstand `Expand`/4737. Help wist de selectie en een oude
koopknop mag daardoor niets kopen. Info-Close keert terug naar het productraster.

Benodigde bron-sprites:4735,4737,8386,8387,788;297/frame715 hergebruik via bestaande sprite-map.
De bestaande `OsrsInterfacePortTool dmm2-ui --reuse` verzamelt deze sprites en alloceert twee
native667CS2-adapters: bronkey7155=negen-int positie/maatbridge; bronkey7153=drie-int plus string
op-caption/graphicbridge. Deze helpers zijn een vertaling voor de bestaande667client, geen
byte-exacte imports van de oorspronkelijke OSRS-scripts. Ze gebruiken bewezen nativeprimitives
2000/2001/2300/2105 en het bestaande667footerformaat; hun locals,args en stackvolgorde hebben
aparte binaire contracttests. Er zijn geen verzonnen lokale ids: allocator en `--reuse` publiceren
werkelijke `script`- en `sprite`-regels in `cfg/dmm2/interfaces.tsv`, dat Boot naast ids.tsv inleest.

Claude: eerst dezelfde bestaande importtool in PLAN op de actuele cache, daarna zijn gebundelde
apply, nieuwe interface-map naar de testdata en de echte-clientronde uit checklist14b.
De oude193-componentenimport bevat de nieuwe helpers/kinderen nog niet. Zonder bridge weigert
View een layoutwissel zonder item-/geldmutatie. Unit/nativePLAN is geen grafische of live-PASS.
Native PLAN tegen GPT-A's twee immutable fysieke cachekopieen staat in `PREFLIGHT-PHASE6.sh`;
alleen PLAN, geen originele-cachemutatie. Verse bewijsstatus volgt in PHASE6-VALIDATION.json.
