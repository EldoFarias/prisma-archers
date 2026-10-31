"""Store listing texts and screenshot captions in the 8 languages of Prisma Archers.

Writes store/listings/<play-locale>/{title,short_description,full_description}.txt and checks the
Play Console limits (30 / 80 / 4000 characters). Captions are for the store screenshots.

    python3 store/listings.py
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))

L = {}

L["pt-BR"] = dict(
    title="Prisma Archers: Duelo de Arco",
    short="Puxe, mire e solte! Duelos de arco e flecha com tiro na cabeça e câmera lenta.",
    full="""Puxe o arco, calcule o ângulo e a força e solte a flecha! Em Prisma Archers você enfrenta arqueiros em duelos por turnos: acerte o corpo para tirar vida ou acerte a cabeça para vencer na hora, com câmera lenta e o boneco caindo de um jeito sempre diferente. Fácil de aprender, difícil de largar.

★ COMO JOGAR
• Arraste para trás e solte para atirar: quanto mais puxa, mais forte
• Acerte a cabeça para um HEADSHOT instantâneo
• Cuidado com o vento, com inimigos mais altos ou mais baixos e com capacetes

★ FASES INFINITAS
• Inimigos cada vez mais certeiros, distâncias maiores e vento mais forte
• Duelo chefe a cada 10 fases, com armadura e prêmio triplo
• Mapa de mundos: campo, deserto, montanhas nevadas, vulcão, noite estrelada, sakura, castelo e selva

★ SOBREVIVÊNCIA
• Um inimigo atrás do outro: quantos você derruba?
• Bata o seu recorde a cada partida

★ TODO DIA TEM NOVIDADE
• Desafio diário igual para todos
• Roleta da sorte, três missões por dia e baú de 7 dias
• Conquistas com recompensas

★ PERSONALIZE SEU ARQUEIRO
• 13 visuais: patrulheiro, pirata, caubói, viking, ninja, cavaleiro, samurai, mago, robô, esqueleto, rei e anjo
• 9 arcos, do arco de madeira ao laser e ao dragão
• Rastros de flecha: neon, estrelas, fogo azul, pó de ouro e arco-íris

★ LEVE E FLUIDO
• Física de boneco de pano, câmera que segue a flecha e câmera lenta nos golpes finais
• Funciona sem internet

Grátis, com anúncios. Não precisa de conta.
""",
    captions=["Puxe, mire e solte!", "HEADSHOT em câmera lenta", "Duelo chefe a cada 10 fases",
              "Vento e alturas diferentes", "Sobrevivência sem fim", "13 arqueiros e 9 arcos", "Roleta, missões e conquistas"],
)

L["en-US"] = dict(
    title="Prisma Archers: Bow Duel",
    short="Pull, aim and release! Archery duels with headshots and slow motion.",
    full="""Pull back the bow, judge the angle and the power, and let the arrow fly! In Prisma Archers you face other archers in turn-based duels: hit the body to take health away, or hit the head to win on the spot, with slow motion and a ragdoll fall that is never the same. Easy to learn, hard to put down.

★ HOW TO PLAY
• Drag back and release to shoot: the further you pull, the stronger the shot
• Hit the head for an instant HEADSHOT
• Watch out for wind, enemies higher or lower than you, and helmets

★ ENDLESS LEVELS
• Sharper enemies, longer distances and stronger wind as you go
• A boss duel every 10 levels, armored and with a triple reward
• World map: meadow, desert, snowy peaks, volcano, starry night, sakura, castle and jungle

★ SURVIVAL
• One enemy after another: how many can you take down?
• Beat your record every run

★ SOMETHING NEW EVERY DAY
• A daily challenge, the same for everyone
• Lucky wheel, three missions a day and a 7-day chest
• Achievements with rewards

★ CUSTOMIZE YOUR ARCHER
• 13 looks: ranger, pirate, cowboy, viking, ninja, knight, samurai, wizard, robot, skeleton, king and angel
• 9 bows, from the wooden bow to laser and dragon
• Arrow trails: neon, stars, blue fire, gold dust and rainbow

★ LIGHT AND SMOOTH
• Ragdoll physics, a camera that follows the arrow and slow motion on finishing blows
• Plays offline

Free, with ads. No account needed.
""",
    captions=["Pull, aim and release!", "Slow-motion HEADSHOTS", "Boss duel every 10 levels",
              "Wind and height to master", "Endless Survival", "13 archers, 9 bows", "Wheel, missions, achievements"],
)

L["es-ES"] = dict(
    title="Prisma Archers: Duelo de Arco",
    short="¡Tensa, apunta y suelta! Duelos de arco con tiros a la cabeza y cámara lenta.",
    full="""¡Tensa el arco, calcula el ángulo y la fuerza y suelta la flecha! En Prisma Archers te enfrentas a otros arqueros en duelos por turnos: acierta en el cuerpo para quitar vida o en la cabeza para ganar al instante, con cámara lenta y una caída de muñeco que nunca es igual. Fácil de aprender, difícil de dejar.

★ CÓMO JUGAR
• Arrastra hacia atrás y suelta para disparar: cuanto más tiras, más fuerte
• Acierta en la cabeza para un HEADSHOT instantáneo
• Cuidado con el viento, los enemigos más altos o más bajos y los cascos

★ NIVELES INFINITOS
• Enemigos más certeros, distancias mayores y viento más fuerte
• Duelo de jefe cada 10 niveles, con armadura y premio triple
• Mapa de mundos: pradera, desierto, picos nevados, volcán, noche estrellada, sakura, castillo y selva

★ SUPERVIVENCIA
• Un enemigo tras otro: ¿cuántos puedes derribar?
• Supera tu récord en cada partida

★ NOVEDADES CADA DÍA
• Desafío diario, igual para todos
• Ruleta de la suerte, tres misiones al día y cofre de 7 días
• Logros con recompensas

★ PERSONALIZA TU ARQUERO
• 13 aspectos: guardabosques, pirata, vaquero, vikingo, ninja, caballero, samurái, mago, robot, esqueleto, rey y ángel
• 9 arcos, del arco de madera al láser y al dragón
• Estelas de flecha: neón, estrellas, fuego azul, polvo de oro y arcoíris

★ LIGERO Y FLUIDO
• Física de muñeco de trapo, cámara que sigue la flecha y cámara lenta en los golpes finales
• Funciona sin internet

Gratis, con anuncios. No necesita cuenta.
""",
    captions=["¡Tensa, apunta y suelta!", "HEADSHOT a cámara lenta", "Jefe cada 10 niveles",
              "Viento y alturas", "Supervivencia sin fin", "13 arqueros y 9 arcos", "Ruleta, misiones y logros"],
)

L["fr-FR"] = dict(
    title="Prisma Archers: Duel à l'arc",
    short="Tire, vise et lâche ! Duels à l'arc avec tirs à la tête et ralenti.",
    full="""Bande l'arc, évalue l'angle et la puissance, et lâche la flèche ! Dans Prisma Archers, tu affrontes d'autres archers en duels au tour par tour : touche le corps pour enlever des PV ou la tête pour gagner sur-le-champ, avec un ralenti et une chute de pantin jamais identique. Facile à prendre en main, difficile à lâcher.

★ COMMENT JOUER
• Tire en arrière et relâche pour tirer : plus tu tires, plus c'est fort
• Vise la tête pour un HEADSHOT immédiat
• Attention au vent, aux ennemis plus hauts ou plus bas et aux casques

★ NIVEAUX INFINIS
• Des ennemis plus précis, des distances plus longues et un vent plus fort
• Un duel de boss tous les 10 niveaux, en armure et avec récompense triple
• Carte des mondes : prairie, désert, pics enneigés, volcan, nuit étoilée, sakura, château et jungle

★ SURVIE
• Un ennemi après l'autre : combien peux-tu en abattre ?
• Bats ton record à chaque partie

★ DU NOUVEAU CHAQUE JOUR
• Un défi du jour, le même pour tous
• Roue de la chance, trois missions par jour et coffre de 7 jours
• Succès avec récompenses

★ PERSONNALISE TON ARCHER
• 13 tenues : rôdeur, pirate, cow-boy, viking, ninja, chevalier, samouraï, sorcier, robot, squelette, roi et ange
• 9 arcs, de l'arc en bois au laser et au dragon
• Traînées de flèche : néon, étoiles, feu bleu, poudre d'or et arc-en-ciel

★ LÉGER ET FLUIDE
• Physique de pantin, caméra qui suit la flèche et ralenti sur les coups finaux
• Fonctionne hors ligne

Gratuit, avec publicités. Aucun compte requis.
""",
    captions=["Tire, vise et lâche !", "HEADSHOT au ralenti", "Un boss tous les 10 niveaux",
              "Vent et hauteurs", "Survie sans fin", "13 archers, 9 arcs", "Roue, missions et succès"],
)

L["de-DE"] = dict(
    title="Prisma Archers: Bogenduell",
    short="Spannen, zielen, loslassen! Bogenduelle mit Kopftreffern und Zeitlupe.",
    full="""Spann den Bogen, schätz Winkel und Kraft ein und lass den Pfeil fliegen! In Prisma Archers trittst du in rundenbasierten Duellen gegen andere Bogenschützen an: Triff den Körper, um Lebenspunkte abzuziehen, oder den Kopf für den sofortigen Sieg – mit Zeitlupe und einem Ragdoll-Sturz, der nie gleich aussieht. Leicht zu lernen, schwer aus der Hand zu legen.

★ SO WIRD GESPIELT
• Nach hinten ziehen und loslassen: Je weiter du ziehst, desto stärker der Schuss
• Triff den Kopf für einen sofortigen HEADSHOT
• Achte auf Wind, höher oder tiefer stehende Gegner und Helme

★ ENDLOSE LEVEL
• Treffsicherere Gegner, größere Entfernungen und stärkerer Wind
• Alle 10 Level ein Boss-Duell, gepanzert und mit dreifacher Belohnung
• Weltkarte: Wiese, Wüste, Schneegipfel, Vulkan, Sternennacht, Sakura, Burg und Dschungel

★ ÜBERLEBEN
• Ein Gegner nach dem anderen: Wie viele schaffst du?
• Knack in jeder Runde deinen Rekord

★ JEDEN TAG ETWAS NEUES
• Tägliche Challenge, für alle gleich
• Glücksrad, drei Missionen pro Tag und 7-Tage-Truhe
• Erfolge mit Belohnungen

★ GESTALTE DEINEN BOGENSCHÜTZEN
• 13 Looks: Waldläufer, Pirat, Cowboy, Wikinger, Ninja, Ritter, Samurai, Zauberer, Roboter, Skelett, König und Engel
• 9 Bögen, vom Holzbogen bis zu Laser und Drache
• Pfeilspuren: Neon, Sterne, blaues Feuer, Goldstaub und Regenbogen

★ LEICHT UND FLÜSSIG
• Ragdoll-Physik, eine Kamera, die dem Pfeil folgt, und Zeitlupe beim letzten Treffer
• Funktioniert offline

Kostenlos, mit Werbung. Kein Konto nötig.
""",
    captions=["Spannen, zielen, loslassen!", "HEADSHOT in Zeitlupe", "Boss alle 10 Level",
              "Wind und Höhen meistern", "Endloses Überleben", "13 Schützen, 9 Bögen", "Glücksrad, Missionen, Erfolge"],
)

L["tr-TR"] = dict(
    title="Prisma Archers: Okçu Düellosu",
    short="Çek, nişan al ve bırak! Kafa vuruşlu ve ağır çekimli ok düelloları.",
    full="""Yayı ger, açıyı ve gücü hesapla, oku bırak! Prisma Archers'ta diğer okçularla sıra tabanlı düellolara girersin: can götürmek için gövdeyi, anında kazanmak için kafayı vur; ağır çekim ve her seferinde farklı bir bez bebek düşüşüyle. Öğrenmesi kolay, bırakması zor.

★ NASIL OYNANIR
• Geri çek ve bırak: ne kadar çekersen o kadar güçlü
• Anında HEADSHOT için kafaya vur
• Rüzgara, daha yüksekte ya da alçakta duran düşmanlara ve miğferlere dikkat et

★ SONSUZ BÖLÜMLER
• Daha isabetli düşmanlar, daha uzun mesafeler ve daha sert rüzgar
• Her 10 bölümde zırhlı ve üç kat ödüllü patron düellosu
• Dünya haritası: çayır, çöl, karlı zirveler, yanardağ, yıldızlı gece, sakura, kale ve orman

★ HAYATTA KALMA
• Bir düşman bitince diğeri gelir: kaçını yenebilirsin?
• Her oyunda rekorunu kır

★ HER GÜN YENİ BİR ŞEY
• Herkes için aynı günlük görev
• Şans çarkı, günde üç görev ve 7 günlük sandık
• Ödüllü başarımlar

★ OKÇUNU ÖZELLEŞTİR
• 13 görünüm: korucu, korsan, kovboy, viking, ninja, şövalye, samuray, büyücü, robot, iskelet, kral ve melek
• Tahta yaydan lazer ve ejderhaya 9 yay
• Ok izleri: neon, yıldızlar, mavi ateş, altın tozu ve gökkuşağı

★ HAFİF VE AKICI
• Bez bebek fiziği, oku takip eden kamera ve son vuruşta ağır çekim
• İnternetsiz oynanır

Ücretsiz, reklamlı. Hesap gerekmez.
""",
    captions=["Çek, nişan al, bırak!", "Ağır çekim HEADSHOT", "Her 10 bölümde patron",
              "Rüzgar ve yükseklik", "Sonsuz hayatta kalma", "13 okçu, 9 yay", "Çark, görevler, başarımlar"],
)

L["id"] = dict(
    title="Prisma Archers: Duel Panah",
    short="Tarik, bidik, lepas! Duel panah dengan tembakan kepala dan gerak lambat.",
    full="""Tarik busurnya, perkirakan sudut dan kekuatan, lalu lepaskan panah! Di Prisma Archers kamu melawan pemanah lain dalam duel bergiliran: kenai tubuh untuk mengurangi nyawa, atau kenai kepala untuk menang seketika, dengan gerak lambat dan jatuhan ragdoll yang selalu berbeda. Mudah dipelajari, sulit berhenti.

★ CARA BERMAIN
• Tarik ke belakang lalu lepas: makin jauh ditarik, makin kuat
• Kenai kepala untuk HEADSHOT instan
• Waspadai angin, musuh yang lebih tinggi atau rendah, dan helm

★ LEVEL TANPA AKHIR
• Musuh makin jitu, jarak makin jauh, angin makin kencang
• Duel bos setiap 10 level, berbaju zirah dengan hadiah tiga kali
• Peta dunia: padang, gurun, puncak salju, gunung api, malam berbintang, sakura, kastil, dan hutan

★ BERTAHAN
• Satu musuh demi satu: berapa yang bisa kamu jatuhkan?
• Pecahkan rekormu setiap main

★ ADA YANG BARU SETIAP HARI
• Tantangan harian, sama untuk semua
• Roda keberuntungan, tiga misi per hari, dan peti 7 hari
• Pencapaian berhadiah

★ KUSTOMISASI PEMANAHMU
• 13 tampilan: penjaga hutan, bajak laut, koboi, viking, ninja, ksatria, samurai, penyihir, robot, tengkorak, raja, dan malaikat
• 9 busur, dari busur kayu hingga laser dan naga
• Jejak panah: neon, bintang, api biru, debu emas, dan pelangi

★ RINGAN DAN LANCAR
• Fisika ragdoll, kamera yang mengikuti panah, dan gerak lambat di pukulan terakhir
• Bisa dimainkan offline

Gratis, dengan iklan. Tanpa akun.
""",
    captions=["Tarik, bidik, lepas!", "HEADSHOT gerak lambat", "Bos setiap 10 level",
              "Angin dan ketinggian", "Bertahan tanpa akhir", "13 pemanah, 9 busur", "Roda, misi, pencapaian"],
)

L["hi-IN"] = dict(
    title="Prisma Archers: तीरंदाज़ी",
    short="खींचें, निशाना लगाएं और छोड़ें! हेडशॉट और स्लो मोशन वाले तीरंदाज़ी मुकाबले।",
    full="""धनुष खींचें, कोण और ताकत का अंदाज़ा लगाएं और तीर छोड़ें! Prisma Archers में आप दूसरे तीरंदाज़ों से बारी-बारी वाले मुकाबले करते हैं: HP घटाने के लिए शरीर पर मारें या तुरंत जीतने के लिए सिर पर, स्लो मोशन और हर बार अलग तरह से गिरते रैगडॉल के साथ। सीखना आसान, छोड़ना मुश्किल।

★ कैसे खेलें
• पीछे खींचें और छोड़ें: जितना ज़्यादा खींचेंगे, उतना तेज़ शॉट
• तुरंत HEADSHOT के लिए सिर पर मारें
• हवा, ऊपर या नीचे खड़े दुश्मनों और हेलमेट से सावधान

★ अनगिनत लेवल
• ज़्यादा सटीक दुश्मन, लंबी दूरी और तेज़ हवा
• हर 10 लेवल पर कवच वाला बॉस और तीन गुना इनाम
• दुनिया का नक्शा: मैदान, रेगिस्तान, बर्फ़ीली चोटियाँ, ज्वालामुखी, तारों भरी रात, साकुरा, किला और जंगल

★ सर्वाइवल
• एक के बाद एक दुश्मन: आप कितनों को हरा सकते हैं?
• हर बार अपना रिकॉर्ड तोड़ें

★ हर दिन कुछ नया
• सबके लिए एक जैसी दैनिक चुनौती
• किस्मत का पहिया, रोज़ तीन मिशन और 7 दिन का संदूक
• इनाम वाली उपलब्धियाँ

★ अपना तीरंदाज़ सजाएं
• 13 लुक: रेंजर, समुद्री डाकू, काउबॉय, वाइकिंग, निंजा, शूरवीर, समुराई, जादूगर, रोबोट, कंकाल, राजा और फ़रिश्ता
• लकड़ी के धनुष से लेज़र और ड्रैगन तक 9 धनुष
• तीर के ट्रेल: नियॉन, सितारे, नीली आग, सोने की धूल और इंद्रधनुष

★ हल्का और स्मूद
• रैगडॉल फ़िज़िक्स, तीर के पीछे चलता कैमरा और आखिरी वार पर स्लो मोशन
• बिना इंटरनेट के खेलें

मुफ़्त, विज्ञापनों के साथ। अकाउंट की ज़रूरत नहीं।
""",
    captions=["खींचें, निशाना, छोड़ें!", "स्लो मोशन HEADSHOT", "हर 10 लेवल पर बॉस",
              "हवा और ऊँचाई", "अंतहीन सर्वाइवल", "13 तीरंदाज़, 9 धनुष", "पहिया, मिशन, उपलब्धियाँ"],
)

if __name__ == "__main__":
    for loc, t in L.items():
        for key, limit in (("title", 30), ("short", 80), ("full", 4000)):
            n = len(t[key].strip())
            assert n <= limit, (loc, key, n, limit)
        assert len(t["captions"]) == 7, loc
        d = os.path.join(HERE, "listings", loc)
        os.makedirs(d, exist_ok=True)
        for key, name in (("title", "title"), ("short", "short_description"), ("full", "full_description")):
            with open(os.path.join(d, name + ".txt"), "w", encoding="utf-8", newline="\n") as f:
                f.write(t[key].strip() + "\n")
        print(f"{loc:6} title={len(t['title']):2} short={len(t['short']):2} full={len(t['full'].strip()):4}")
