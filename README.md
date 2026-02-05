# InvertiCalNew 📊⚽

**InvertiCalNew** è l'evoluzione definitiva del tool di analisi statistica per leghe di Fantacalcio gestite con **FantaCalcio Manager (FCM)**.

Nato come fork del progetto originale [Invertical di Arghami](https://github.com/arghami/invertical), questo software è stato completamente riscritto nel motore di calcolo, nell'interfaccia e nella generazione dei report per offrire una visione "meritocratica" e statistica profonda della tua lega.

![Java](https://img.shields.io/badge/Java-Swing-orange.svg)
![Version](https://img.shields.io/badge/release-2.0_Revo-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)

---

## 🌟 Cosa c'è di Nuovo

Rispetto alla versione classica, **InvertiCalNew** introduce funzionalità avanzate per un'analisi più moderna:

* **⚡ Elaborazione Batch Multi-Competizione:** Possibilità di elaborare in sequenza Serie A, Serie B, Coppe, ecc., generando un report unico e navigabile.
* **📈 Performance Index (Classifica Avulsa Reale):** Calcolo della somma delle medie punti ottenute ogni giornata contro *tutti* gli avversari, per premiare la costanza di rendimento.
* **🎨 Nuovo Output "Revo Style":** Report HTML moderni, flat e responsivi, con CSS esterno personalizzabile, pensati per integrarsi con i moderni siti di leghe (es. FMSRevo).
* **🧮 Nuove Statistiche:** Analisi approfondita su fortuna, sfortuna e difficoltà oggettiva del calendario.

---

## 🧠 La Logica di Calcolo

Il software risponde a tre domande fondamentali per ogni fantallenatore:

### 1. "Come sarebbe finita con il calendario di un altro?" (Matrice Incroci)
Questa tabella simula il campionato della tua squadra se avesse affrontato la sequenza di avversari di un'altra squadra.
* **Diagonale (Bianco):** È il tuo punteggio reale in campionato.
* **Celle Colorate:** Indicano la differenza tra la simulazione e la realtà.
    * 🟢 **VERDE (Meglio):** Con quel calendario avresti fatto **più punti** del tuo totale reale. *(Significato: Il tuo calendario reale è stato difficile/sfortunato).*
    * 🔴 **ROSSO (Peggio):** Con quel calendario avresti fatto **meno punti** del tuo totale reale. *(Significato: Il tuo calendario reale ti ha favorito).*

### 2. "Chi è la squadra più costante?" (Performance Index)
Oltre alla classifica reale, viene calcolata la **Classifica Avulsa**.
Ogni giornata, la tua squadra gioca virtualmente contro **tutte le altre squadre del girone**.
* **Indice Performance:** Viene calcolato sommando, giornata per giornata, la media punti ottenuta contro l'intero campionato.
    * *Esempio:* Se fai 72 punti e batti 6 squadre, pareggi con 1 e perdi con 2 (su 9 avversari), il tuo indice di giornata è `(6*3 + 1*1 + 0) / 9 = 2.11`.
    * Questo valore premia la costanza di rendimento indipendentemente dall'avversario di turno.

### 3. Curiosità & Statistiche
Alla fine di ogni report, il software calcola 4 metriche speciali:

1.  **Squadra più Fortunata/Sfortunata:** Basata sul differenziale tra i Punti Reali e la media dei punti che la squadra avrebbe ottenuto con tutti gli altri calendari possibili.
2.  **Calendario più Facile/Difficile:** Analizza quale "percorso" (sequenza di avversari) ha permesso alla maggioranza delle squadre di ottenere il punteggio più alto (o più basso).
3.  **"La fortuna è cieca" (Sopravvalutata):** La squadra con il divario positivo più ampio tra i punti in classifica reale e il suo Performance Index. *(Ha molti punti in classifica, ma prestazioni medie).*
4.  **"La sfiga ci vede benissimo" (Sottovalutata):** La squadra con il divario negativo più ampio. *(Ha prestazioni top, ma ha raccolto pochi punti in classifica).*

---

## 🛠️ Installazione e Utilizzo

### Requisiti
* PC con sistema operativo Windows.
* [Java Runtime Environment (JRE)](https://www.java.com/it/download/) installato.

### Istruzioni
1.  Scarica l'ultima release (`InvertiCalNew.jar`).
2.  Avvia il programma (consigliato usare il file `.bat` se fornito, o doppio click sul `.jar`).
3.  **Carica:** Seleziona il file `.fcm` della tua lega.
4.  **Seleziona:** Scegli la Competizione e il Girone dal menu a tendina.
5.  **Coda:** Clicca **"Aggiungi alla Coda"** (puoi aggiungere più gironi, es. Serie A e Serie B).
6.  **Esegui:** Clicca **"AVVIA ELABORAZIONE"**.

### Output
Il software genererà nella cartella di destinazione:
* Un file `riepilogo_competizioni.html` (il report completo).
* Un file `incroci.css` (il foglio di stile, modificabile).
* Una cartella `incrodet` con i dettagli delle singole giornate.

---

## 📜 Crediti

* **Original Idea:** [Arghami/invertical](https://github.com/arghami/invertical)
* **New Development & Logic:** Mauz
* **Libraries:** Jackcess / UCanAccess

---

*InvertiCalNew by Mauz 2025*