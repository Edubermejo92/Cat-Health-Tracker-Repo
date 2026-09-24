package padelpulseapp2.netlify.app

data class Language(val id: String, val flag: String, val name: String, val voiceLang: String)

data class VoiceData(
    val zero: String, val fifteen: String, val thirty: String, val forty: String,
    val all: String, val deuce: String, val advantage: String, val game: String,
    val set: String, val goldenPoint: String, val fault: String, val doubleFault: String, val serves: String
)

data class UIStrings(
    val local: String, val away: String, val sets: String, val games: String,
    val settings: String, val chooseLang: String, val mode: String, val play: String,
    val newMatch: String, val undo: String, val fault: String, val doubleFault: String,
    val serves: String, val teamA: String, val teamB: String, val goldenPt: String,
    val superTB: String, val bestOf: String, val theme: String, val voice: String,
    val bluetooth: String, val skip: String, val done: String, val connected: String,
    val notConnected: String, val chMode: String, val start: String, val chooseMode: String,
    val solo: String, val watchCtrl: String, val phoneCtrl: String, val btMode: String
)

object Translations {
    val langs = listOf(
        Language("es", "🇪🇸", "Español", "es-ES"),
        Language("en", "🇬🇧", "English", "en-GB"),
        Language("it", "🇮🇹", "Italiano", "it-IT"),
        Language("fr", "🇫🇷", "Français", "fr-FR"),
        Language("de", "🇩🇪", "Deutsch", "de-DE"),
        Language("fi", "🇫🇮", "Suomi", "fi-FI"),
        Language("zh", "🇨🇳", "中文", "zh-CN"),
        Language("ja", "🇯🇵", "日本語", "ja-JP"),
        Language("ar", "🇸🇦", "عربي", "ar-SA"),
        Language("pt", "🇵🇹", "Português", "pt-PT"),
        Language("ko", "🇰🇷", "한국어", "ko-KR"),
        Language("nl", "🇳🇱", "Nederlands", "nl-NL"),
        Language("sv", "🇸🇪", "Svenska", "sv-SE"),
        // El movil ofrece ruso: sin esta entrada el idioma no viajaria al reloj
        Language("ru", "🇷🇺", "Русский", "ru-RU")
    )

    val vd = mapOf(
        "es" to VoiceData("cero", "quince", "treinta", "cuarenta", "iguales", "iguales", "ventaja", "juego", "set", "punto de oro", "Falta.", "Doble falta.", "Saca"),
        "en" to VoiceData("love", "fifteen", "thirty", "forty", "all", "deuce", "advantage", "game", "set", "golden point", "Fault.", "Double fault.", "Serves"),
        "it" to VoiceData("zero", "quindici", "trenta", "quaranta", "pari", "parità", "vantaggio", "gioco", "set", "punto d'oro", "Fallo.", "Doppio fallo.", "Batte"),
        "fr" to VoiceData("zéro", "quinze", "trente", "quarante", "égalité", "égalité", "avantage", "jeu", "set", "point doré", "Faute.", "Double faute.", "Sert"),
        "de" to VoiceData("null", "fünfzehn", "dreißig", "vierzig", "gleich", "einstand", "vorteil", "spiel", "satz", "goldener punkt", "Fehler.", "Doppelfehler.", "Aufschlag"),
        "fi" to VoiceData("nolla", "viisitoista", "kolmekymmentä", "neljäkymmentä", "tasapeli", "deuce", "etu", "peli", "erä", "kultainen piste", "Virhe.", "Kaksoishuti.", "Syöttää"),
        "zh" to VoiceData("零", "十五", "三十", "四十", "平局", "平分", "占先", "局", "盘", "黄金分", "失误。", "双误。", "发球"),
        "ja" to VoiceData("ラブ", "フィフティーン", "サーティ", "フォーティ", "オール", "デュース", "アドバンテージ", "ゲーム", "セット", "ゴールデンポイント", "フォルト。", "ダブルフォルト。", "サーブ"),
        "ar" to VoiceData("صفر", "خمسة عشر", "ثلاثون", "أربعون", "تعادل", "مساواة", "ميزة", "لعبة", "مجموعة", "نقطة ذهبية", "خطأ.", "خطأ مزدوج.", "يرسل"),
        "pt" to VoiceData("zero", "quinze", "trinta", "quarenta", "iguais", "deuce", "vantagem", "jogo", "set", "ponto de oro", "Falta.", "Dupla falta.", "Saca"),
        "ko" to VoiceData("러브", "피프틴", "서티", "포티", "올", "듀스", "어드밴티지", "게임", "세트", "골든포인트", "폴트.", "더블 폴트.", "서브"),
        "nl" to VoiceData("nul", "vijftien", "dertig", "veertig", "gelijk", "deuce", "voordeel", "game", "set", "gouden punt", "Fout.", "Dubbele fout.", "Serveert"),
        "sv" to VoiceData("noll", "femton", "trettio", "fyrtio", "lika", "deuce", "fördel", "game", "set", "gyllene poäng", "Fel.", "Dubbelfel.", "Servar"),
        "ru" to VoiceData("ноль", "пятнадцать", "тридцать", "сорок", "ровно", "ровно", "больше", "гейм", "сет", "золотое очко", "Ошибка.", "Двойная ошибка.", "Подаёт")
    )

    val ui = mapOf(
        "es" to UIStrings("Local", "Visita", "Sets", "Juegos", "Ajustes", "Elige tu idioma", "Modo", "Jugar", "Nueva Partida", "Deshacer", "Falta", "Doble Falta", "Saca", "Pareja A", "Pareja B", "Punto Oro", "Super TB", "Mejor de", "Tema", "Altavoz", "Bluetooth", "Omitir", "Listo", "Conectado", "Sin conexión", "Cambiar modo", "Comenzar", "Elige el modo", "Solo", "Manda Reloj", "Manda Móvil", "Código BT"),
        "en" to UIStrings("Local", "Away", "Sets", "Games", "Settings", "Choose language", "Mode", "Play", "New Match", "Undo", "Fault", "Double Fault", "Serves", "Team A", "Team B", "Golden Pt", "Super TB", "Best of", "Theme", "Voice", "Bluetooth", "Skip", "Done", "Connected", "Not connected", "Change Mode", "Start", "Choose mode", "Solo", "Watch Ctrl", "Phone Ctrl", "BT Code"),
        "it" to UIStrings("Locale", "Ospite", "Set", "Giochi", "Impostazioni", "Scegli la lingua", "Modalità", "Gioca", "Nuova Partida", "Annulla", "Fallo", "Doppio Fallo", "Batte", "Coppia A", "Coppia B", "Pt Oro", "Super TB", "Al mejor de", "Tema", "Voce", "Bluetooth", "Salta", "Fatto", "Connesso", "Non connesso", "Cambia modalità", "Inizia", "Scegli modalità", "Solo", "Controlla Orologio", "Controlla Telefono", "Codice BT"),
        "fr" to UIStrings("Local", "Visiteur", "Sets", "Jeux", "Réglages", "Choisir la langue", "Mode", "Jouer", "Nouveau Match", "Annuler", "Faute", "Double Faute", "Sert", "Équipe A", "Équipe B", "Pt Or", "Super TB", "Au meilleur de", "Thème", "Voix", "Bluetooth", "Passer", "Prêt", "Connecté", "Non connecté", "Changer de mode", "Commencer", "Choisir le mode", "Solo", "Contrôle Montre", "Contrôle Téléphone", "Code BT"),
        "de" to UIStrings("Lokal", "Gast", "Sätze", "Spiele", "Einstellungen", "Sprache wählen", "Modus", "Spielen", "Neues Spiel", "Rückgängig", "Fehler", "Doppelfehler", "Aufschlag", "Team A", "Team B", "Gold Pkt", "Super TB", "Bester von", "Thema", "Stimme", "Bluetooth", "Überspringen", "Fertig", "Verbunden", "Nicht verbunden", "Modus ändern", "Starten", "Modus wählen", "Solo", "Uhr Steuerung", "Handy Steuerung", "BT Code"),
        "fi" to UIStrings("Koti", "Vieras", "Erät", "Pelit", "Asetukset", "Valitse kieli", "Tila", "Pelaa", "Uusi Ottelu", "Kumoa", "Virhe", "Kaksoishuti", "Syöttää", "Joukkue A", "Joukkue B", "Kulta Pist", "Super TB", "Paras", "Teema", "Ääni", "Bluetooth", "Ohita", "Valmis", "Yhdistetty", "Ei yhteyttä", "Vaihda tilaa", "Aloita", "Valitse tila", "Solo", "Kello Ohjaus", "Puhelin Ohjaus", "BT Koodi"),
        "zh" to UIStrings("本地", "访客", "盘", "局", "设置", "选择语言", "模式", "开始", "新比赛", "撤销", "失误", "双误", "发球", "A队", "B队", "黄金分", "超级TB", "决胜", "主题", "声音", "蓝牙", "跳过", "完成", "已连接", "未连接", "切换模式", "开始", "选择模式", "单机", "手表控制", "手机控制", "BT码"),
        "ja" to UIStrings("ホーム", "アウェイ", "セット", "ゲーム", "設定", "言語を選択", "モード", "プレイ", "新しい試合", "元に戻す", "フォルト", "ダブルフォルト", "サーブ", "チームA", "チームB", "ゴールドPt", "スーパーTB", "ベスト", "テーマ", "音声", "Bluetooth", "スキップ", "完了", "接続済み", "未接続", "モード変更", "開始", "モード選択", "ソロ", "時計制御", "電話制御", "BTコード"),
        "ar" to UIStrings("محلي", "زائر", "مجموعات", "ألعاب", "إعدادات", "اختر اللغة", "الوضع", "العب", "مباراة جديدة", "تراجع", "خطأ", "خطأ مزدوج", "يرسل", "الفريق أ", "الفريق ب", "نقطة ذهبية", "سوبر TB", "الأفضل", "السمة", "الصوت", "بلوتوث", "تخطي", "تم", "متصل", "غير متصل", "تغيير الوضع", "بدء", "اختر الوضع", "منفرد", "تحكم الساعة", "تحكم الهاتف", "رمز BT"),
        "pt" to UIStrings("Local", "Visita", "Sets", "Jogos", "Configurações", "Escolha o idioma", "Modo", "Jogar", "Nova Partida", "Desfazer", "Falta", "Dupla Falta", "Saca", "Dupla A", "Dupla B", "Pt Ouro", "Super TB", "Melhor de", "Tema", "Voz", "Bluetooth", "Ignorar", "Pronto", "Conectado", "Não conectado", "Mudar modo", "Começar", "Escolha o modo", "Solo", "Controle Relógio", "Controle Telefone", "Código BT"),
        "ko" to UIStrings("홈", "어웨이", "세트", "게임", "설정", "언어 선택", "모드", "플레이", "새 경기", "실행 취소", "폴트", "더블 폴트", "서브", "팀 A", "팀 B", "골든 Pt", "슈퍼 TB", "베스트", "테마", "음성", "블루투스", "건너뛰기", "완료", "연결됨", "연결 안됨", "모드 변경", "시작", "모드 선택", "솔로", "시계 제어", "전화 제어", "BT 코드"),
        "nl" to UIStrings("Thuis", "Gast", "Sets", "Games", "Instellingen", "Kies taal", "Modus", "Spelen", "Nieuw Spel", "Ongedaan", "Fout", "Dubbele Fout", "Serveert", "Team A", "Team B", "Goud Pt", "Super TB", "Best van", "Thema", "Stem", "Bluetooth", "Overslaan", "Klaar", "Verbonden", "Niet verbonden", "Modus wijzigen", "Starten", "Kies modus", "Solo", "Horloge Ctrl", "Telefoon Ctrl", "BT Code"),
        "sv" to UIStrings("Hemma", "Borta", "Set", "Spel", "Inställningar", "Välj språk", "Läge", "Spela", "Ny Match", "Ångra", "Fel", "Dubbelfel", "Servar", "Lag A", "Lag B", "Guld Pt", "Super TB", "Bäst av", "Tema", "Röst", "Bluetooth", "Hoppa över", "Klar", "Ansluten", "Ej ansluten", "Ändra läge", "Starta", "Välj läge", "Solo", "Klocka Ctrl", "Telefon Ctrl", "BT Kod"),
        "ru" to UIStrings("Хозяева", "Гости", "Сеты", "Геймы", "Настройки", "Выберите язык", "Режим", "Играть", "Новый матч", "Отменить", "Ошибка", "Двойная ошибка", "Подаёт", "Пара A", "Пара B", "Золотое очко", "Супер ТБ", "До", "Тема", "Голос", "Bluetooth", "Пропустить", "Готово", "Подключено", "Нет связи", "Сменить режим", "Начать", "Выберите режим", "Соло", "Ведут часы", "Ведёт телефон", "Код BT")
    )
}
