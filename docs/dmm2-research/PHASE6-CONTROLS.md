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
naast het normale productraster. Alleen de bestaande verborgen controls weer zichtbaar maken
zou lege controls en verkeerde maten opleveren. De huidige port heeft voor deze controls nog
geen dynamische grafische kinderen of een volledige expanded-view-uitvoering. Dit blijft een
expliciete UI-pariteitsgrens, geen beweerde OSRS-aftekening.

## PREFLIGHT voor Claude

Benodigde bron-sprites voor de volgende volledige control-port: **4735, 4737, 8386, 8387, 788**
(297 en het 715-frame hergebruiken zodra de bestaande sprite-map dat bewijst). Gebruik bestaande
`OsrsInterfacePortTool dmm2-ui --reuse` met de echte interface-map; die batch moet na toevoeging
van deze bronkinderen hun sprites verzamelen. Het huidige commando alleen importeert ze nog
niet vanzelf. Geen lokale sprite-ids kiezen, geen huidige 193-componentenclaim voor een nog
niet gebouwde uitbreiding. Voor die uitbreiding eerst componentcontract, unit-PLAN en een PLAN
tegen een cachekopie, daarna Claude's eigen gebundelde apply en testclientronde.

Deze bronbijlage lost het brongat op. De huidige servercorrecties voor raw packetops en de
normale verborgen header 14 werken met de al toegepaste widgets en vereisen geen cachewijziging.
