# NovaMobile

Natív Kotlin Android alkalmazás magyar hangasszisztenssel. A folyamatos beszédfelismeréshez egy valódi eszközön legyen telepítve/engedélyezve magyar nyelvű Google Speech Services; a TTS a készülék magyar beszédmotorját használja.

Mondd például: **„Nova, nyisd meg a Wi-Fi beállításokat”** vagy **„Nova, nyisd meg a térképet”**.

Minden támogatott művelethez a `CommandRouter` 15 normalizált, természetes magyar megfogalmazást generál (öt kérő forma három magyar tárgynév-szinonimával). A biztonsági Android korlátozások miatt a Wi‑Fi, Bluetooth, hotspot, VPN, helyadatok és mobilhálózat esetén a megfelelő rendszeroldal nyílik meg; ott a felhasználó kapcsolhatja őket.
