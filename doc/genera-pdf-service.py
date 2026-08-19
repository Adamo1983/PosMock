# -*- coding: utf-8 -*-
"""
Genera doc/Service-Android-in-PosMock.pdf.

Il PDF e' un artefatto: **questo file e' il sorgente**. Per correggere il documento si
modifica qui e si rigenera, mai il PDF a mano.

    python doc/genera-pdf-service.py

Serve ReportLab. Il documento non ha dipendenze dal codice dell'app: le porzioni di codice
citate sono copie, quindi vanno riviste quando il codice vero cambia.
"""

import os
import sys

from reportlab.lib import colors
from reportlab.lib.enums import TA_JUSTIFY
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    PageTemplate,
    Paragraph,
    Preformatted,
    Spacer,
    Table,
    TableStyle,
)

TITOLO = "I Service in Android"
SOTTOTITOLO = "e come PosMock li usa"
INTESTAZIONE = "I Service in Android — e come PosMock li usa"
PIEDE = "PosMock — documentazione interna"
DATA = "19/08/2026"

BLU = colors.HexColor("#1F4E79")
BLU_CHIARO = colors.HexColor("#DCE6F1")
GRIGIO = colors.HexColor("#5B5F66")
GRIGIO_RIGA = colors.HexColor("#C6CBD4")
SFONDO_CODICE = colors.HexColor("#F4F6F9")
SFONDO_NOTA = colors.HexColor("#FFF6E0")
BORDO_NOTA = colors.HexColor("#E0B04A")

MARGINE = 24.1 * mm
LARGHEZZA = A4[0] - 2 * MARGINE

# --------------------------------------------------------------------------- stili

corpo = ParagraphStyle(
    "corpo", fontName="Helvetica", fontSize=9.6, leading=13.4,
    alignment=TA_JUSTIFY, spaceAfter=7,
)
corpo_nota = ParagraphStyle(
    "corpo_nota", parent=corpo, fontSize=8.2, leading=11.4,
    textColor=GRIGIO, spaceBefore=2, spaceAfter=9,
)
titolo = ParagraphStyle(
    "titolo", fontName="Helvetica-Bold", fontSize=23, leading=28, textColor=BLU,
)
sottotitolo = ParagraphStyle(
    "sottotitolo", fontName="Helvetica", fontSize=11.5, leading=16, textColor=GRIGIO,
)
meta = ParagraphStyle(
    "meta", fontName="Helvetica", fontSize=8.4, leading=12, textColor=GRIGIO, spaceAfter=14,
)
h1 = ParagraphStyle(
    "h1", fontName="Helvetica-Bold", fontSize=15, leading=19, textColor=BLU,
    spaceBefore=16, spaceAfter=8,
)
h2 = ParagraphStyle(
    "h2", fontName="Helvetica-Bold", fontSize=11, leading=14, textColor=BLU,
    spaceBefore=11, spaceAfter=5,
)
punto = ParagraphStyle(
    "punto", parent=corpo, leftIndent=12, bulletIndent=2, spaceAfter=6,
)
cella = ParagraphStyle("cella", fontName="Helvetica", fontSize=8.4, leading=11.4)
cella_forte = ParagraphStyle("cella_forte", parent=cella, fontName="Helvetica-Bold")
cella_codice = ParagraphStyle("cella_codice", parent=cella, fontName="Courier", fontSize=7.6)
nota = ParagraphStyle(
    "nota", fontName="Helvetica-Bold", fontSize=9.2, leading=12.8, alignment=TA_JUSTIFY,
)


def p(testo, stile=corpo):
    return Paragraph(testo, stile)


def elenco(testo):
    return Paragraph(testo, punto, bulletText="•")


def codice(testo, dimensione=7.4):
    """Blocco monospaziato in un riquadro. Preformatted non interpreta markup: i < > & passano."""
    stile = ParagraphStyle(
        "codice%s" % dimensione, fontName="Courier", fontSize=dimensione,
        leading=dimensione * 1.32, textColor=colors.HexColor("#1A1A1A"),
    )
    tabella = Table([[Preformatted(testo.strip("\n"), stile)]], colWidths=[LARGHEZZA])
    tabella.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), SFONDO_CODICE),
        ("BOX", (0, 0), (-1, -1), 0.5, GRIGIO_RIGA),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ]))
    return [tabella, Spacer(1, 9)]


def avviso(testo):
    """Riquadro giallo: sono i punti in cui il documento smette di descrivere e avverte."""
    tabella = Table([[Paragraph(testo, nota)]], colWidths=[LARGHEZZA])
    tabella.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), SFONDO_NOTA),
        ("BOX", (0, 0), (-1, -1), 0.6, BORDO_NOTA),
        ("LEFTPADDING", (0, 0), (-1, -1), 9),
        ("RIGHTPADDING", (0, 0), (-1, -1), 9),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
    ]))
    return [tabella, Spacer(1, 10)]


def tabella(righe, larghezze, mono=()):
    """Prima riga sempre intestazione. `mono` elenca le colonne da rendere monospaziate."""
    dati = []
    for i, riga in enumerate(righe):
        fila = []
        for j, testo in enumerate(riga):
            if i == 0:
                stile = cella_forte
            elif j in mono:
                stile = cella_codice
            else:
                stile = cella
            fila.append(Paragraph(testo, stile))
        dati.append(fila)
    t = Table(dati, colWidths=larghezze, repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), BLU_CHIARO),
        ("TEXTCOLOR", (0, 0), (-1, 0), BLU),
        ("GRID", (0, 0), (-1, -1), 0.4, GRIGIO_RIGA),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    return [t, Spacer(1, 10)]


def cornice(canvas, doc):
    """Intestazione dalla seconda pagina in poi, piede su tutte."""
    canvas.saveState()
    canvas.setFont("Helvetica", 7.5)
    canvas.setFillColor(GRIGIO)
    canvas.drawString(MARGINE - 6, 34, PIEDE)
    canvas.drawRightString(A4[0] - MARGINE + 6, 34, "pag. %d" % doc.page)
    if doc.page > 1:
        canvas.drawString(MARGINE, A4[1] - 40, INTESTAZIONE)
        canvas.setStrokeColor(GRIGIO_RIGA)
        canvas.setLineWidth(0.4)
        canvas.line(MARGINE, A4[1] - 46, A4[0] - MARGINE, A4[1] - 46)
    canvas.restoreState()


# --------------------------------------------------------------------------- contenuto

def costruisci():
    s = []

    s.append(p(TITOLO, titolo))
    s.append(p(SOTTOTITOLO, sottotitolo))
    s.append(Spacer(1, 6))
    t = Table([[""]], colWidths=[LARGHEZZA], rowHeights=[1.1])
    t.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), BLU)]))
    s.append(t)
    s.append(Spacer(1, 8))
    s.append(p("PosMock — simulatore di terminale POS &nbsp;—&nbsp; %s" % DATA, meta))

    s.append(p(
        "Questo documento risponde a due domande messe in fila. La prima e' generale: che cos'e' "
        "davvero un Service in Android, cosa risolve e perche' la piattaforma ha continuato a "
        "stringerne le regole a ogni versione. La seconda e' specifica: perche' PosMock ne ha uno, "
        "come e' costruito e — soprattutto — perche' il server TCP che simula il terminale "
        "non vive dentro di lui."))
    s.append(p(
        "La seconda parte e' la piu' utile da rileggere fra sei mesi: le scelte fatte qui sembrano "
        "arbitrarie finche' non si ricorda il guasto che dovevano evitare."))

    # ------------------------------------------------------------------ 1
    s.append(p("1. Che cos'e' un Service", h1))
    s.append(p(
        "Un Service e' un componente dell'applicazione, dichiarato nel manifest, che svolge un "
        "lavoro senza interfaccia utente e con una vita indipendente da quella delle Activity. "
        "Un'Activity muore quando l'utente la lascia; un Service no."))

    s.append(p("Le tre cose che un Service non e'", h2))
    s.append(p("Quasi tutti gli errori con i Service nascono da uno di questi tre malintesi."))
    s.append(elenco(
        "<b>Non e' un thread.</b> Un Service gira sul main thread del processo, esattamente come "
        "una Activity. Metterci dentro una lettura da socket bloccante congela la UI. Il lavoro in "
        "background va comunque messo su una coroutine o un thread proprio."))
    s.append(elenco(
        "<b>Non e' un processo separato.</b> Vive nello stesso processo dell'app, quindi condivide "
        "memoria, singleton e stato statico. (Si puo' forzare un processo diverso con "
        "<font name='Courier'>android:process</font>, ma allora smette di condividere tutto ed e' "
        "un'altra storia.)"))
    s.append(elenco(
        "<b>Non e' “codice che gira per sempre”.</b> Il sistema puo' ucciderlo. Quello "
        "che un Service compra e' <b>priorita', non immortalita'</b>."))
    s.append(p(
        "Detta nel modo che conta: un Service e' il modo di dichiarare al sistema che il processo "
        "sta facendo qualcosa che vale anche senza nessuno che guardi lo schermo. Tutto il resto "
        "discende da qui."))

    # ------------------------------------------------------------------ 2
    s.append(p("2. Il problema che risolve: il low memory killer", h1))
    s.append(p(
        "Android non ha swap. Quando la memoria scarseggia, il sistema uccide processi, e li "
        "sceglie per rango di importanza. Il rango non lo decide l'app: lo deduce il sistema da "
        "quali componenti sono vivi al suo interno."))
    s += tabella(
        [["Rango del processo", "Quando", "Rischio di essere ucciso"],
         ["Foreground", "Activity in primo piano, oppure un foreground service",
          "Quasi nullo: solo in emergenza estrema"],
         ["Visible", "Activity visibile ma non a fuoco (dietro un dialog)", "Molto basso"],
         ["Service", "Un service background avviato con startService", "Medio"],
         ["Cached / Empty", "Nessun componente attivo, tenuto solo per riaprirlo in fretta",
          "Alto: e' il primo della lista"]],
        [LARGHEZZA * 0.24, LARGHEZZA * 0.42, LARGHEZZA * 0.34])
    s.append(p(
        "Un'app senza componenti attivi che finisce in background scivola subito in cached. Da li' "
        "viene uccisa senza preavviso, senza callback, senza nulla: le socket aperte si chiudono, "
        "le coroutine spariscono. Il foreground service e' l'unico strumento che porta il processo "
        "in cima a quella lista mentre l'utente sta guardando altro."))

    # ------------------------------------------------------------------ 3
    s.append(p("3. Started e Bound: due modi di usarlo", h1))
    s += tabella(
        [["", "Started service", "Bound service"],
         ["Come parte", "<font name='Courier'>startService()</font> / "
                        "<font name='Courier'>startForegroundService()</font>",
          "<font name='Courier'>bindService()</font>"],
         ["Chi lo tiene vivo",
          "Se stesso, finche' non chiama <font name='Courier'>stopSelf()</font> o qualcuno "
          "<font name='Courier'>stopService()</font>",
          "I client collegati: quando l'ultimo si stacca, muore"],
         ["Comunicazione", "Intent in ingresso; per uscire servono broadcast, callback o stato "
                           "condiviso", "Un IBinder: chiamate dirette ai metodi"],
         ["Usato per", "Lavoro che deve finire a prescindere dalla UI",
          "Una UI che comanda un componente e ne legge lo stato"]],
        [LARGHEZZA * 0.19, LARGHEZZA * 0.44, LARGHEZZA * 0.37])
    s.append(p(
        "PosMock usa il primo e ignora il secondo: <font name='Courier'>onBind()</font> ritorna "
        "null. Il perche' e' nel capitolo 7 — la UI non ha nulla da chiedere al service."))

    # ------------------------------------------------------------------ 4
    s.append(p("4. Perche' oggi “foreground” non e' una scelta stilistica", h1))
    s.append(p(
        "Fino ad Android 7 un service in background poteva vivere indefinitamente. E' stato "
        "abusato al punto che ogni versione successiva ha aggiunto un vincolo. Vale la pena "
        "conoscerli, perche' spiegano quasi tutte le righe del nostro manifest."))
    s += tabella(
        [["Versione", "Vincolo introdotto"],
         ["8.0 (API 26)",
          "<b>Background execution limits</b>: un service avviato mentre l'app e' in background "
          "viene fermato dopo circa un minuto. Nasce <font name='Courier'>startForegroundService()"
          "</font>, che obbliga a chiamare <font name='Courier'>startForeground()</font> con una "
          "notifica entro 5 secondi, pena una ANR."],
         ["10 (API 29)",
          "Compare <font name='Courier'>foregroundServiceType</font> per l'accesso a posizione, "
          "camera e microfono."],
         ["12 (API 31)",
          "Vietato avviare un foreground service mentre l'app e' in background, salvo eccezioni "
          "elencate."],
         ["13 (API 33)",
          "<font name='Courier'>POST_NOTIFICATIONS</font> diventa un permesso runtime: senza, il "
          "service parte ma la sua notifica non si vede."],
         ["14 (API 34)",
          "<font name='Courier'>foregroundServiceType</font> obbligatorio per ogni FGS, con un "
          "permesso dedicato per tipo. Il tipo va passato anche a "
          "<font name='Courier'>startForeground()</font>."],
         ["15 (API 35)",
          "Il tipo <font name='Courier'>dataSync</font> ha un tetto di 6 ore complessive ogni 24, "
          "dopo le quali il sistema lo ferma."]],
        [LARGHEZZA * 0.17, LARGHEZZA * 0.83])
    s += avviso(
        "Quando invece non serve un Service. Per lavoro differibile — sincronizzare, "
        "caricare, ripulire — la risposta giusta e' WorkManager: sopravvive al riavvio del "
        "telefono e rispetta i vincoli di batteria. Il Service serve quando il lavoro e' continuo, "
        "adesso, e non puo' essere rimandato. Il nostro caso e' esattamente questo: una socket in "
        "ascolto non e' rinviabile a quando il telefono sara' in carica.")

    # ------------------------------------------------------------------ 5
    s.append(p("5. Ciclo di vita e valore di ritorno di onStartCommand", h1))
    s += codice("""
onCreate()          una sola volta, alla prima creazione
   |
onStartCommand()    a OGNI startService(), anche a service gia' vivo
   |                ritorna una politica di riavvio (tabella qui sotto)
   |
   ...  il service vive; se non fa niente, non "gira": sta solo li'
   |
onDestroy()         su stopSelf(), stopService(), o kill del sistema
""")
    s.append(p(
        "Il valore ritornato da <font name='Courier'>onStartCommand()</font> dice al sistema cosa "
        "fare se il processo viene ucciso lo stesso."))
    s += tabella(
        [["Valore", "Se il sistema uccide il processo"],
         ["START_STICKY", "Ricrea il service appena puo', con un intent nullo. Adatto a chi sa "
                          "ripartire da solo (un player di musica)."],
         ["START_NOT_STICKY", "Non lo ricrea. Adatto a chi, senza il proprio stato in memoria, non "
                              "saprebbe che farsene di una seconda vita."],
         ["START_REDELIVER_INTENT", "Lo ricrea riconsegnando l'ultimo intent. Adatto a un lavoro "
                                    "descritto per intero dall'intent (un download)."]],
        [LARGHEZZA * 0.28, LARGHEZZA * 0.72], mono=(0,))

    # ------------------------------------------------------------------ 6
    s.append(p("6. PosMock: il guasto da cui nasce tutto", h1))
    s.append(p(
        "PosMock finge di essere un terminale POS. Apre un ServerSocket e risponde a chi lo "
        "interroga come farebbe un terminale vero, cosi' da poter provare il percorso di "
        "pagamento senza hardware — e soprattutto da poter riprodurre a comando i casi che sul "
        "banco non si riescono a provocare. Chi lo interroga sono due tratte diverse: la catena "
        "col palmare, che passa dal middleware, e la cassa da sola, che al terminale ci parla "
        "anche senza."))
    s += codice("""
Giano (cassa) <-> Ermes (palmare) --WS--> UniquePosManager --TCP--> PosMock
Giano (cassa) -----------------------------------------------TCP--> PosMock
                                                                    (invece del POS vero)
""", 7.6)
    s.append(p(
        "Il banco di prova resta acceso a lungo: un telefono appoggiato alla scrivania, la "
        "sessione di test che dura un turno intero, lo schermo che si spegne fra una prova e "
        "l'altra. E qui c'e' la trappola."))
    s += avviso(
        "Se il processo di PosMock muore, la socket si chiude a meta' transazione. Il middleware "
        "vede una connessione caduta e un esito che non arriva: esattamente i sintomi del "
        "pagamento orfano, cioe' del guasto che si sta indagando. Si passerebbero ore a cercare "
        "il bug nel middleware mentre il colpevole e' il telefono andato in Doze. Un banco di "
        "prova che produce da solo il sintomo che deve misurare non e' uno strumento: e' una "
        "fonte di errori.")
    s.append(p(
        "Il foreground service esiste per questo, e per nient'altro. Non e' una scelta di "
        "architettura: e' una difesa contro un falso positivo."))

    # ------------------------------------------------------------------ 7
    s.append(p("7. La decisione centrale: il server non vive nel Service", h1))
    s.append(p(
        "L'istinto sarebbe mettere l'accept loop dentro <font name='Courier'>MockServerService"
        "</font>. In PosMock e' l'opposto: il server e' <font name='Courier'>MockServerController"
        "</font>, un <font name='Courier'>single</font> Koin che vive quanto il processo, e il "
        "service e' un guscio che gli sta accanto."))
    s += codice("""
+--------------------------- processo dell'app ----------------------------+
|                                                                          |
|   StatusViewModel  --- start()/stop() -->  MockServerController          |
|         ^                                   (single Koin)                |
|         |                                   - ServerSocket + accept loop |
|         +---- StateFlow: state, ------------ - wake lock + wifi lock     |
|                activeConnections            - decide chi serve la conn.  |
|                                                     |                    |
|                                    startForegroundService()              |
|                                                     v                    |
|                                          MockServerService               |
|                                          - tiene alto il rango           |
|                                          - mostra la notifica            |
+--------------------------------------------------------------------------+
""")
    s.append(p("Cosa si guadagna", h2))
    s.append(elenco(
        "<b>La UI legge lo stato senza intermediari.</b> "
        "<font name='Courier'>StatusViewModel</font> osserva direttamente gli StateFlow del "
        "controller. Niente IBinder, niente broadcast, niente serializzazione dello stato dentro "
        "e fuori da un Intent."))
    s.append(elenco(
        "<b>Il controllo e' invertito.</b> Non e' il service ad accendere il server: e' il "
        "controller che, appena la socket e' in bind, chiama "
        "<font name='Courier'>startForegroundService()</font>, e che in "
        "<font name='Courier'>stop()</font> lo spegne. Il ciclo di vita del service segue quello "
        "del server, non viceversa."))
    s.append(elenco(
        "<b>Il server e' testabile e indipendente da Android.</b> La logica di protocollo non sa "
        "nemmeno che esista un service."))
    s += codice("""
// MockServerController.start() -- ordine non casuale
serverJob = scope.launch {
    socket = ServerSocket().apply { reuseAddress = true; bind(...) }
    acquireLocks()       // wake lock + wifi lock: PRIMA di dichiararsi pronto
    startService()       // il foreground service, ora che la porta e' davvero aperta
    _state.value = ServerState.Running(config.protocol, config.port)
    while (isActive) { ... accept() ... }
}

private fun startService() {
    appContext.startForegroundService(Intent(appContext, MockServerService::class.java))
}
""")
    s.append(p(
        "Il service parte dopo il bind riuscito: se la porta fosse occupata comparirebbe una "
        "notifica “in ascolto” su un server che non c'e'.", corpo_nota))

    # ------------------------------------------------------------------ 8
    s.append(p("8. Il Service, riga per riga", h1))

    s.append(p("La dichiarazione nel manifest", h2))
    s += codice("""
<service
    android:name=".service.MockServerService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Simulatore di terminale POS per test di integrazione: tiene
            aperto un server TCP mentre lo schermo e' spento" />
</service>
""")
    s.append(elenco(
        "<b>exported=\"false\"</b>: nessuna altra app deve poterlo avviare."))
    s.append(elenco(
        "<b>specialUse e non dataSync</b>: come visto al capitolo 4, su Android 15 un FGS "
        "<font name='Courier'>dataSync</font> viene fermato dopo 6 ore complessive nelle 24. Un "
        "banco di prova puo' restare acceso per un turno intero, quindi quel tetto lo taglierebbe "
        "a meta' pomeriggio. <font name='Courier'>specialUse</font> non ha il tetto, ma richiede "
        "la <font name='Courier'>&lt;property&gt;</font> che ne descrive il motivo — e su "
        "Google Play una revisione manuale. PosMock e' uno strumento interno e non passa dallo "
        "store, quindi il compromesso conviene."))

    s.append(p("onCreate: notifica entro cinque secondi", h2))
    s += codice("""
override fun onCreate() {
    super.onCreate()
    createChannel()
    startForegroundWithNotification(buildNotification("Avvio in corso..."))
    observeState()
}

private fun startForegroundWithNotification(notification: Notification) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
        startForeground(NOTIFICATION_ID, notification)
    }
}
""")
    s.append(p(
        "La notifica iniziale dice “Avvio in corso…” perche' la promessa fatta a "
        "<font name='Courier'>startForegroundService()</font> va mantenuta subito: il testo "
        "definitivo arriva un istante dopo dallo StateFlow. Da Android 14 il tipo va ripetuto "
        "anche qui, non basta il manifest."))

    s.append(p("La notifica si aggiorna da sola", h2))
    s += codice("""
private fun observeState() {
    scope.launch {
        serverController.state.collectLatest { state ->
            when (state) {
                is ServerState.Running -> updateNotification(
                    "${state.protocol.label} in ascolto sulla porta ${state.port}"
                )
                ServerState.Starting -> updateNotification("Avvio in corso...")
                // niente notifica di "fermo": si smonta, vedi sotto
                ServerState.Stopped  -> removeNotificationAndStop()
                is ServerState.Error -> reportErrorAndStop(state.message)
            }
        }
    }
}
""")
    s.append(p(
        "Il service consuma lo stesso StateFlow che alimenta la UI. Non lo produce, non lo "
        "duplica: c'e' una sorgente di verita' sola, ed e' il controller. La notifica e' una "
        "seconda vista sullo stesso dato."))
    s.append(p(
        "Due rami su quattro pero' non aggiornano e basta: <font name='Courier'>Stopped</font> e "
        "<font name='Courier'>Error</font> sono i due modi in cui il service finisce, e vanno "
        "guardati uno per uno."))

    s.append(p("Il ramo Stopped smonta, non aggiorna", h2))
    s.append(p(
        "Sembrerebbe naturale scrivere <font name='Courier'>ServerState.Stopped -&gt; "
        "updateNotification(\"Server fermo\")</font>, ed e' quello che il service faceva fino al "
        "19/08/2026. Il sintomo: a banco spento l'icona restava accesa in barra di stato, con una "
        "notifica “PosMock — Server fermo” che non se ne andava e non si scartava "
        "nemmeno con lo swipe."))
    s.append(p(
        "Il motivo sta in un ordine che dal codice del service non si vede. "
        "<font name='Courier'>MockServerController.stop()</font> porta lo stato a "
        "<font name='Courier'>Stopped</font> e <b>subito dopo</b> chiama "
        "<font name='Courier'>stopService()</font>. Lato sistema, ActivityManager toglie la "
        "notifica del foreground service <b>appena riceve lo stop</b> e consegna l'"
        "<font name='Courier'>onDestroy</font> al main thread <b>dopo</b>. In quella finestra il "
        "collector — gia' accodato sul main thread dall'emissione dello StateFlow — gira "
        "e ripubblica la notifica su un service che il sistema ha gia' spogliato. Da li' in poi "
        "quella notifica non ha piu' nessuno dietro, e con "
        "<font name='Courier'>setOngoing(true)</font> non e' nemmeno scartabile."))
    s += codice("""
private fun removeNotificationAndStop() {
    clearNotification()
    stopSelf()
}

/** Solo la notifica del service: quella d'errore ha un altro id e deve restare. */
private fun clearNotification() {
    stopForeground(STOP_FOREGROUND_REMOVE)
    notificationManager().cancel(NOTIFICATION_ID)
}

override fun onDestroy() {
    scope.cancel()
    clearNotification()   // per l'ordine inverso: qui il ramo Stopped non gira mai
    super.onDestroy()
}
""")
    s.append(p(
        "La stessa pulizia sta in <font name='Courier'>onDestroy()</font> perche' i due ordini "
        "sono entrambi possibili: se e' l'<font name='Courier'>onDestroy</font> ad arrivare per "
        "primo, <font name='Courier'>scope.cancel()</font> uccide il collector e il ramo "
        "<font name='Courier'>Stopped</font> non gira mai. Ognuna delle due chiamate copre il caso "
        "che l'altra manca, e una <font name='Courier'>cancel()</font> su una notifica che non c'e' "
        "piu' non costa niente."))
    s += avviso(
        "La regola generale: la notifica di un foreground service non si aggiorna sulla via "
        "dell'arresto, si smonta. Un aggiornamento “innocuo” in quella finestra la fa "
        "rinascere orfana — ed e' un bug che il codice non mostra, perche' il ramo sbagliato "
        "sembra il piu' ragionevole dei quattro.")

    s.append(p("Il ramo Error lascia una seconda notifica", h2))
    s.append(p(
        "Se il server muore per conto suo — l'interfaccia di rete che cade mentre il banco e' "
        "acceso — il service non ha piu' niente da tenere vivo e si ferma; ma prima lascia in "
        "tendina una notifica d'errore <b>con un id diverso</b>, perche' quella del foreground "
        "service viene portata via dal sistema insieme al service. E' scartabile, a differenza "
        "dell'altra, perche' non c'e' piu' niente da fermare. Per lo stesso motivo "
        "<font name='Courier'>clearNotification()</font> cancella solo il proprio id: se prendesse "
        "tutto, si porterebbe via anche l'avviso appena messo."))
    s += avviso(
        "Un banco che sparisce in silenzio e' il problema, non la soluzione. Senza quella seconda "
        "notifica, una caduta di rete lascerebbe il telefono muto e apparentemente a posto: si "
        "tornerebbe a cercare il guasto nel middleware. Per lo stesso motivo il controller, nel "
        "ramo d'errore, rilascia subito wake lock e wifi lock — tenerli per un server che non "
        "ascolta piu' e' solo batteria buttata.")

    s.append(p("L'azione “Ferma”", h2))
    s += codice("""
val stop = PendingIntent.getService(
    this, 1,
    Intent(this, MockServerService::class.java).apply { action = ACTION_STOP },
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
        scope.launch {
            serverController.stop()  // il service non spegne il server: glielo chiede
            stopSelf()
        }
        return START_NOT_STICKY
    }
    return START_NOT_STICKY
}
""")
    s.append(elenco(
        "Il banco si spegne dalla tendina, senza riaprire l'app: comodo quando il telefono e' gia' "
        "appoggiato con lo schermo bloccato."))
    s.append(elenco(
        "<b>FLAG_IMMUTABLE</b> e' obbligatorio da Android 12 (API 31): un PendingIntent mutabile "
        "potrebbe essere riempito da altri."))
    s.append(elenco(
        "L'azione passa comunque da <font name='Courier'>serverController.stop()</font>, che "
        "chiude le socket, rilascia i lock e aggiorna lo stato. Se il service chiudesse per conto "
        "suo, la UI mostrerebbe un server acceso che non esiste piu'."))
    s.append(elenco(
        "<font name='Courier'>stop()</font> e' sospendibile, perche' aspetta davvero che l'accept "
        "loop sia finito: al ritorno la porta e' libera per un riavvio immediato. Da qui il "
        "<font name='Courier'>launch</font> — e il <font name='Courier'>stopSelf()</font> "
        "messo dopo, dato che l'<font name='Courier'>onDestroy</font> del service cancellerebbe lo "
        "scope in cui quella pulizia sta girando."))
    s.append(elenco(
        "Dentro, la pulizia gira in <font name='Courier'>NonCancellable</font>: gli scope che la "
        "ospitano muoiono per conto loro — il <font name='Courier'>viewModelScope</font> alla "
        "rotazione dello schermo — e una pulizia interrotta a meta' lascerebbe il wake lock "
        "in mano."))

    s.append(p("Perche' START_NOT_STICKY", h2))
    s.append(p(
        "E' la riga che sorprende di piu', e ha una ragione precisa. Se malgrado tutto il sistema "
        "uccide il processo, il server muore con lui: la socket, la configurazione in memoria, le "
        "connessioni aperte, la coroutine dell'accept loop. Un service resuscitato da solo si "
        "ritroverebbe senza niente da presidiare e mostrerebbe una notifica “in ascolto” "
        "davanti a una porta chiusa."))
    s += avviso(
        "Meglio un banco spento e visibile che una notifica che mente. Con START_NOT_STICKY la "
        "sparizione e' evidente e l'operatore riavvia; con START_STICKY sarebbe silenziosa, e si "
        "tornerebbe a cercare il guasto dalla parte sbagliata — cioe' proprio nel modo che "
        "questo service doveva impedire.")

    # ------------------------------------------------------------------ 9
    s.append(p("9. Il Service da solo non basta: le quattro difese", h1))
    s.append(p(
        "Il foreground service tiene alto il rango del processo, ma non impedisce alla CPU di "
        "sospendersi ne' al Wi-Fi di andare in risparmio energetico. Servono tutte e quattro le "
        "protezioni insieme, le stesse gia' collaudate in Ermes."))
    s += tabella(
        [["Difesa", "Contro cosa", "Dove"],
         ["Foreground service <font name='Courier'>specialUse</font>",
          "Il processo ucciso con l'app in background",
          "<font name='Courier'>MockServerService</font>"],
         ["Wake lock parziale", "La CPU sospesa a schermo spento",
          "<font name='Courier'>MockServerController</font>"],
         ["Wifi lock <font name='Courier'>FULL_LOW_LATENCY</font> "
          "(<font name='Courier'>FULL_HIGH_PERF</font> sotto API 29)",
          "Il Wi-Fi in risparmio energetico: il middleware trova un terminale che non risponde",
          "<font name='Courier'>MockServerController</font>"],
         ["<font name='Courier'>FLAG_KEEP_SCREEN_ON</font> + esenzione dalle ottimizzazioni di "
          "batteria",
          "Doze e gli OEM aggressivi (Samsung One UI in testa), che sospendono lo stesso",
          "<font name='Courier'>MainActivity</font>"]],
        [LARGHEZZA * 0.30, LARGHEZZA * 0.42, LARGHEZZA * 0.28])
    s.append(p(
        "Lock presi e rilasciati insieme al server, non insieme al service: stanno in "
        "<font name='Courier'>acquireLocks()</font> / <font name='Courier'>releaseLocks()</font> "
        "del controller, per lo stesso motivo per cui li' sta la socket."))
    s.append(p(
        "L'esenzione dalla batteria viene richiesta a ogni avvio finche' non e' concessa, perche' "
        "la sorgente di verita' e' lo stato del sistema (<font name='Courier'>isExempt()</font>) e "
        "non una preferenza nostra: un reset fatto dall'OEM la toglierebbe senza dirlo a nessuno."))

    # ------------------------------------------------------------------ 10
    s.append(p("10. Le alternative, e perche' sono state scartate", h1))
    s += tabella(
        [["Alternativa", "Perche' no"],
         ["Nessun service, solo l'Activity",
          "Con lo schermo spento il processo scivola in cached e viene ucciso. E' esattamente il "
          "guasto da evitare."],
         ["WorkManager",
          "Fatto per lavoro differibile e a scadenza. Una socket in ascolto non e' rinviabile a "
          "quando il telefono sara' in carica."],
         ["Bound service con IBinder",
          "Aggiungerebbe binder, callback e gestione della disconnessione per ottenere quello che "
          "due StateFlow su un singleton danno gratis."],
         ["Il server dentro il service",
          "Il ciclo di vita della socket seguirebbe quello di un componente Android, e la UI "
          "dovrebbe parlarci via binder o broadcast. Piu' pezzi, stesso risultato."],
         ["<font name='Courier'>dataSync</font> invece di "
          "<font name='Courier'>specialUse</font>",
          "Tetto di 6 ore ogni 24 su Android 15: il banco si spegnerebbe da solo a meta' giornata "
          "di prove."]],
        [LARGHEZZA * 0.30, LARGHEZZA * 0.70])

    # ------------------------------------------------------------------ 11
    s.append(p("11. Riepilogo: chi fa che cosa", h1))
    s += tabella(
        [["File", "Responsabilita'"],
         ["data/server/MockServerController.kt",
          "<b>Il server vero.</b> ServerSocket, accept loop, wake e wifi lock, scelta del protocol "
          "handler, avvio e arresto del service. Singleton di processo."],
         ["service/MockServerService.kt",
          "<b>Solo tre cose:</b> tiene alto il rango del processo, mostra la notifica con l'azione "
          "Ferma, e si smonta — notifica compresa — quando il server si ferma. Nessuna "
          "logica di rete."],
         ["presentation/.../StatusViewModel.kt",
          "Chiama <font name='Courier'>start()</font> / <font name='Courier'>stop()</font> sul "
          "controller e ne osserva gli StateFlow. Non conosce il service."],
         ["presentation/activity/MainActivity.kt",
          "<font name='Courier'>FLAG_KEEP_SCREEN_ON</font>, permesso notifiche, richiesta di "
          "esenzione dalla batteria."],
         ["AndroidManifest.xml",
          "Dichiarazione del service, tipo <font name='Courier'>specialUse</font> con la sua "
          "property, permessi di foreground service e wake lock."],
         ["di/AppModule.kt",
          "Tutti <font name='Courier'>single</font>: un <font name='Courier'>factory</font> "
          "significherebbe due server a contendersi la stessa porta."]],
        [LARGHEZZA * 0.34, LARGHEZZA * 0.66], mono=(0,))

    s.append(Spacer(1, 6))
    s += avviso(
        "In una riga: il Service non serve a far girare il server — serve a impedire che "
        "Android lo faccia sparire nel momento peggiore, a lasciare in tendina un interruttore per "
        "spegnerlo, e a togliersi di mezzo per intero quando il banco si spegne.")

    return s


def main():
    qui = os.path.dirname(os.path.abspath(__file__))
    uscita = os.path.join(qui, "Service-Android-in-PosMock.pdf")

    doc = BaseDocTemplate(
        uscita, pagesize=A4,
        leftMargin=MARGINE, rightMargin=MARGINE,
        topMargin=MARGINE * 0.95, bottomMargin=MARGINE * 0.8,
        title="I Service in Android e come PosMock li usa",
        author="PosMock",
        subject="Foreground service, priorita' dei processi, PosMock",
    )
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="corpo")
    doc.addPageTemplates([PageTemplate(id="pagina", frames=[frame], onPage=cornice)])
    doc.build(costruisci())
    print("scritto %s" % uscita)


if __name__ == "__main__":
    sys.exit(main())
