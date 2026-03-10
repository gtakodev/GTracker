# DevTrack - Developer Work Intelligence Tool

## Product Requirements Document

**Version:** 2.0 - Kotlin Desktop Edition
**Date:** Mars 2026
**Auteur:** -
**Statut:** Draft

---

## Table des matieres

1. [Vue d'ensemble](#1-vue-densemble)
2. [Stack technique](#2-stack-technique)
3. [Modele de domaine](#3-modele-de-domaine)
4. [Architecture applicative](#4-architecture-applicative)
5. [UX / UI Design](#5-ux--ui-design)
6. [Fonctionnalites detaillees](#6-fonctionnalites-detaillees)
7. [Securite](#7-securite)
8. [Exigences non-fonctionnelles](#8-exigences-non-fonctionnelles)
9. [Priorisation et phases de developpement](#9-priorisation-et-phases-de-developpement)
10. [Hors-perimetre (V1)](#10-hors-perimetre-v1)

---

## 1. Vue d'ensemble

### 1.1 Probleme adresse

En tant que developpeur travaillant sur plusieurs tickets Jira en parallele, il est difficile de repondre simplement a ces questions au quotidien :

- Ou est-ce que je passe mon temps chaque jour ?
- Sur quels tickets ai-je travaille cette semaine / ce mois ?
- Quel est mon volume de taches annexes (reunions, reviews, documentation) ?
- Comment generer rapidement un compte rendu d'activite (CRA) mensuel ?

### 1.2 Solution

DevTrack est une **application desktop locale en Kotlin** qui permet de :

- **Planifier et organiser** ses taches quotidiennes
- **Tracker le temps** passe par ticket Jira via un timer actif ou des sessions Pomodoro
- **Generer automatiquement** des exports (standup, CRA hebdomadaire, CRA mensuel)
- **Visualiser sa productivite** et ses patterns de travail

### 1.3 Positionnement

DevTrack **n'est pas un remplacement de Jira**. C'est un layer personnel qui se pose a cote, sans aucune synchronisation bidirectionnelle. Jira reste la source de verite pour les tickets ; DevTrack est la **source de verite pour le temps et l'organisation personnelle**.

### 1.4 Principes directeurs

| Principe | Description |
|---|---|
| **Offline-first** | Aucune connexion reseau requise. Toutes les donnees restent locales. |
| **Rapidite** | Creer une tache en < 3 secondes. Demarrage de l'app < 2 secondes. |
| **Minimalisme** | Pas de sur-ingenierie. L'outil fait une chose et la fait bien. |
| **Vie privee** | Aucune telemetrie, aucune transmission de donnees. |

---

## 2. Stack technique

### 2.1 Vue d'ensemble de la stack

| Couche | Technologie | Justification |
|---|---|---|
| **Langage** | Kotlin (JVM) | Typage fort, concis, ecosysteme JetBrains mature |
| **Framework UI** | Compose for Desktop (Compose Multiplatform) | Declaratif, moderne, support natif Windows/Linux |
| **Design System** | Material Design 3 | Composants integres a Compose, coherent, rapide a implementer |
| **Build System** | Gradle Kotlin DSL | Standard pour Kotlin, excellent support Compose Desktop |
| **Base de donnees** | SQLite + SQLCipher | Offline, leger, chiffre au repos |
| **ORM** | Exposed (JetBrains) | DSL Kotlin type-safe pour SQL, integration naturelle |
| **Logging** | SLF4J + Logback | Standard JVM, configurable, fichiers rotatifs |
| **Packaging** | Compose Desktop packaging (jpackage) | Installeurs natifs .msi/.exe (Windows), .deb/.AppImage (Linux) |
| **Internationalisation** | Resource bundles / fichiers properties | Support FR/EN natif |
| **Tests** | JUnit 5 + MockK + Compose UI Testing | Couverture logique metier + tests UI |

### 2.2 Dependances cles

```kotlin
// build.gradle.kts (extrait)
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Base de donnees
    implementation("org.jetbrains.exposed:exposed-core:<version>")
    implementation("org.jetbrains.exposed:exposed-dao:<version>")
    implementation("org.jetbrains.exposed:exposed-jdbc:<version>")
    implementation("org.jetbrains.exposed:exposed-java-time:<version>")
    implementation("net.zetetic:sqlcipher4:<version>")

    // Logging
    implementation("ch.qos.logback:logback-classic:<version>")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter:<version>")
    testImplementation("io.mockk:mockk:<version>")
}
```

### 2.3 Plateformes cibles

| Plateforme | Support | Distribution |
|---|---|---|
| **Windows 10/11** | Primaire | Installeur `.msi` / `.exe` via jpackage |
| **Linux** (Ubuntu/Fedora) | Primaire | `.deb` + `.AppImage` |
| macOS | Hors-scope V1 | - |

---

## 3. Modele de domaine

L'application est structuree autour de **6 entites principales**.

### 3.1 Task

Represente une unite de travail, liee ou non a un ticket Jira. Supporte les **sous-taches** (relation parent-enfant).

| Champ | Type | Description |
|---|---|---|
| `id` | `UUID` | Identifiant unique |
| `parentId` | `UUID?` | Reference vers la tache parente (null = tache racine) |
| `title` | `String` | Titre libre, peut contenir un identifiant Jira |
| `description` | `String?` | Detail optionnel |
| `category` | `TaskCategory` | enum : DEVELOPMENT, BUGFIX, MEETING, REVIEW, DOCUMENTATION, LEARNING, MAINTENANCE, SUPPORT |
| `jiraTickets` | `List<String>` | Detectes automatiquement via regex sur le titre |
| `status` | `TaskStatus` | enum : TODO, IN_PROGRESS, PAUSED, DONE, ARCHIVED |
| `plannedDate` | `LocalDate?` | Null = backlog, date = planifie pour ce jour |
| `isTemplate` | `Boolean` | Si true, jamais planifie automatiquement |
| `createdAt` | `Instant` | Date de creation |
| `updatedAt` | `Instant` | Date de derniere modification |

**Detection automatique des tickets Jira** via regex appliquee a la creation et modification du titre :

```kotlin
val JIRA_TICKET_REGEX = Regex("[A-Z]{2,10}-\\d+")
```

**Sous-taches :**
- Une tache peut avoir N sous-taches (relation `parentId`)
- Une sous-tache herite de la `plannedDate` du parent si non definie
- Le temps total d'une tache parente inclut le temps de ses sous-taches
- Profondeur maximale : 1 niveau (pas de sous-sous-taches) pour garder la simplicite

### 3.2 WorkSession

Represente une periode de travail ciblee sur une tache unique. **Une seule tache par session** ; changer de tache cloture automatiquement la session en cours.

| Champ | Type | Description |
|---|---|---|
| `id` | `UUID` | Identifiant unique |
| `taskId` | `UUID` | Reference vers la Task |
| `date` | `LocalDate` | Jour de la session |
| `startTime` | `Instant` | Debut de la session |
| `endTime` | `Instant?` | Fin (null = session orpheline) |
| `source` | `SessionSource` | enum : TIMER, MANUAL, POMODORO |
| `notes` | `String?` | Note libre sur la session |

### 3.3 SessionEvent

Chaque changement d'etat d'une session est enregistre avec un timestamp. C'est la **source de verite pour le calcul du temps**.

| Champ | Type | Description |
|---|---|---|
| `id` | `UUID` | Identifiant unique |
| `sessionId` | `UUID` | Reference vers la WorkSession |
| `type` | `EventType` | enum : START, PAUSE, RESUME, END |
| `timestamp` | `Instant` | Moment exact de l'evenement |

**Regles de calcul du temps effectif :**
- Temps effectif = somme des periodes entre START/RESUME et PAUSE/END
- Les periodes de pause sont exclues du calcul

### 3.4 TaskCategory

| Identifiant | Label FR | Label EN | Couleur |
|---|---|---|---|
| `DEVELOPMENT` | Developpement | Development | Bleu `#3B82F6` |
| `BUGFIX` | Correction de bug | Bug Fix | Rouge `#EF4444` |
| `MEETING` | Reunion | Meeting | Violet `#8B5CF6` |
| `REVIEW` | Code review | Code Review | Orange `#F97316` |
| `DOCUMENTATION` | Documentation | Documentation | Vert `#22C55E` |
| `LEARNING` | Apprentissage | Learning | Cyan `#06B6D4` |
| `MAINTENANCE` | Maintenance | Maintenance | Gris `#6B7280` |
| `SUPPORT` | Support | Support | Jaune `#EAB308` |

### 3.5 TemplateTask

Taches recurrentes que l'utilisateur peut instancier dans n'importe quel jour d'un simple clic ou via commande rapide. Elles **ne se creent jamais automatiquement**.

| Champ | Type | Description |
|---|---|---|
| `id` | `UUID` | Identifiant unique |
| `title` | `String` | Titre du template |
| `category` | `TaskCategory` | Categorie pre-remplie |
| `defaultDurationMin` | `Int?` | Duree typique en minutes (optionnel) |

### 3.6 UserSettings

Configuration persistante de l'utilisateur.

| Champ | Type | Defaut | Description |
|---|---|---|---|
| `id` | `UUID` | - | Identifiant unique |
| `locale` | `String` | `"fr"` | Langue de l'interface (fr / en) |
| `theme` | `ThemeMode` | `SYSTEM` | LIGHT, DARK, ou SYSTEM |
| `inactivityThresholdMin` | `Int` | `30` | Seuil d'inactivite en minutes |
| `hoursPerDay` | `Double` | `8.0` | Seuil pour conversion heures -> jours dans les CRA |
| `halfDayThreshold` | `Double` | `4.0` | Seuil pour conversion en demi-journee |
| `pomodoroWorkMin` | `Int` | `25` | Duree de travail Pomodoro (minutes) |
| `pomodoroBreakMin` | `Int` | `5` | Duree de pause Pomodoro (minutes) |
| `pomodoroLongBreakMin` | `Int` | `15` | Duree de pause longue Pomodoro (minutes) |
| `pomodoroSessionsBeforeLong` | `Int` | `4` | Nombre de sessions avant une pause longue |

---

## 4. Architecture applicative

### 4.1 Architecture en couches

```
+-------------------------------------------------------+
|                   UI Layer (Compose)                   |
|  Screens / Components / Navigation / Theme / i18n      |
+-------------------------------------------------------+
|               ViewModel / State Layer                  |
|  ViewModels (par ecran) + UI State (data classes)      |
+-------------------------------------------------------+
|                  Domain / Use Cases                    |
|  Logique metier pure (calcul temps, parsing, export)   |
+-------------------------------------------------------+
|                  Repository Layer                      |
|  Abstraction d'acces aux donnees (interfaces)          |
+-------------------------------------------------------+
|                  Data / Persistence                    |
|  Exposed DAO + SQLite/SQLCipher                        |
+-------------------------------------------------------+
|                  Infrastructure                        |
|  System tray, notifications OS, fichiers, logging      |
+-------------------------------------------------------+
```

### 4.2 Structure de packages

```
com.devtrack/
  app/                    # Point d'entree, configuration DI, Application
  ui/
    theme/                # Material 3 theme, couleurs, typographie
    navigation/           # Navigation entre ecrans
    components/           # Composants reutilisables (Timer, TaskCard, CommandPalette...)
    screens/
      today/              # Vue Aujourd'hui
      backlog/            # Vue Backlog
      timeline/           # Timeline du jour
      calendar/           # Vue Calendrier/Semaine
      settings/           # Parametres
      templates/          # Gestion des templates
    i18n/                 # Ressources de traduction FR/EN
  viewmodel/              # ViewModels par ecran
  domain/
    model/                # Entites du domaine (Task, WorkSession, etc.)
    usecase/              # Cas d'utilisation (StartSession, GenerateReport, etc.)
    service/              # Services metier (TimeCalculator, JiraParser, etc.)
  data/
    repository/           # Interfaces des repositories
    repository/impl/      # Implementations Exposed/SQLite
    database/             # Configuration DB, migrations, tables Exposed
  infrastructure/
    systray/              # Integration system tray
    notification/         # Notifications OS natives
    export/               # Generateurs de rapports (Markdown, texte)
    logging/              # Configuration logging
    backup/               # Export/import de la base de donnees
```

### 4.3 Gestion de l'etat (State Management)

Pattern **MVVM** (Model-View-ViewModel) avec Compose :

```kotlin
// Exemple - TodayViewModel
class TodayViewModel(
    private val taskRepository: TaskRepository,
    private val sessionRepository: SessionRepository,
    private val timeCalculator: TimeCalculator
) {
    // Etat observable par l'UI
    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    // Etat du timer actif
    private val _activeSession = MutableStateFlow<ActiveSessionState?>(null)
    val activeSession: StateFlow<ActiveSessionState?> = _activeSession.asStateFlow()

    fun startTask(taskId: UUID) { /* ... */ }
    fun pauseSession() { /* ... */ }
    fun resumeSession() { /* ... */ }
    fun stopSession() { /* ... */ }
}

data class TodayUiState(
    val tasks: List<TaskWithTime> = emptyList(),
    val totalTimeToday: Duration = Duration.ZERO,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### 4.4 Injection de dependances

Utilisation d'un **service locator leger** ou de **Koin** pour l'injection de dependances :

```kotlin
// Koin module
val appModule = module {
    single { DatabaseFactory.create() }
    single<TaskRepository> { TaskRepositoryImpl(get()) }
    single<SessionRepository> { SessionRepositoryImpl(get()) }
    single { TimeCalculator(get()) }
    single { JiraTicketParser() }
    viewModel { TodayViewModel(get(), get(), get()) }
    viewModel { BacklogViewModel(get()) }
}
```

---

## 5. UX / UI Design

### 5.1 Principes UX

| Principe | Application |
|---|---|
| **Rapidite avant tout** | La creation de tache et le demarrage du timer doivent etre quasi instantanes. La command palette (`Ctrl+K`) est le point d'entree principal. |
| **Information progressive** | La vue Aujourd'hui montre l'essentiel. Les details sont accessibles a la demande (expand, click). |
| **Feedback immediat** | Chaque action (start timer, pause, done) produit un retour visuel immediat (animation, changement de couleur, notification). |
| **Minimum de clics** | Les actions les plus frequentes (start, pause, stop, switch) sont accessibles en 1-2 clics ou via raccourci. |
| **Pas de friction** | Pas de modales de confirmation pour les actions courantes. Les actions destructives (supprimer) demandent confirmation. |

### 5.2 Theme et Design System

**Material Design 3** avec Compose, supporte **Dark mode + Light mode** avec bascule manuelle ou suivi du theme systeme.

**Palette de couleurs :**

| Role | Light | Dark |
|---|---|---|
| Primary | `#2563EB` (Bleu) | `#60A5FA` |
| Surface | `#FFFFFF` | `#1E1E2E` |
| Background | `#F8FAFC` | `#11111B` |
| Error | `#DC2626` | `#F87171` |
| Timer actif | `#16A34A` (Vert) | `#4ADE80` |
| Timer pause | `#EAB308` (Jaune) | `#FACC15` |

**Typographie :**
- Titres : `Inter` ou `JetBrains Sans` (sans-serif)
- Code / tickets Jira : `JetBrains Mono` (monospace)

### 5.3 Layout principal

```
+------------------------------------------------------------------+
| [Logo] DevTrack          [Timer: 01:23:45 - DPD-1423]  [?] [=]  |
+----------+-------------------------------------------------------+
|          |                                                       |
| SIDEBAR  |                CONTENU PRINCIPAL                      |
|          |                                                       |
| Aujourd  |  +-------------------------------------------+        |
| 'hui     |  | Taches du jour                            |        |
|          |  | +---------------------------------------+ |        |
| Backlog  |  | | [>] DPD-1423 Fix pagination   1h23m   | |        |
|          |  | | [ ] DPD-2456 Analyse cache    0h00m   | |        |
| Timeline |  | | [>] Daily standup             0h30m   | |        |
|          |  | +---------------------------------------+ |        |
| Calendr. |  +-------------------------------------------+        |
|          |                                                       |
| Rapports |  +-------------------------------------------+        |
|          |  | Backlog (drag pour planifier)             |        |
| Parametr |  | +---------------------------------------+ |        |
| es       |  | | Learn Rust                            | |        |
|          |  | | Refactor logging module               | |        |
+----------+-------------------------------------------------------+
| Status: Session active depuis 1h23m | Total aujourd'hui: 5h42m  |
+------------------------------------------------------------------+
```

### 5.4 Composants UI principaux

#### 5.4.1 Command Palette (`Ctrl+K`)

Dialogue central accessible depuis n'importe quel ecran. Supporte :
- **Recherche de taches** par titre ou ticket Jira
- **Commandes rapides** prefixees par `/`
- **Creation rapide** de tache avec parsing intelligent

```
+---------------------------------------------+
| > /start DPD-1423                           |
|---------------------------------------------|
| /start <ticket>    Demarre une tache        |
| /pause             Pause la session active  |
| /done              Cloture et marque done   |
| /switch <ticket>   Switch vers autre tache  |
| /plan <ticket> today  Planifie pour auj.    |
| /template <nom>    Instancie un template    |
| /report today      Export du jour           |
| /report week       Rapport hebdomadaire     |
| /report month      CRA mensuel             |
+---------------------------------------------+
```

**Parsing intelligent du titre :**
Saisir `DPD-1423 fix pagination #bugfix` cree automatiquement :
- Titre : `DPD-1423 fix pagination`
- Tickets Jira : `["DPD-1423"]`
- Categorie : `BUGFIX`

Pattern de detection de categorie via hashtag :

| Saisie | Categorie |
|---|---|
| `#dev` ou `#development` | DEVELOPMENT |
| `#bug` ou `#bugfix` | BUGFIX |
| `#meet` ou `#meeting` | MEETING |
| `#review` | REVIEW |
| `#doc` ou `#documentation` | DOCUMENTATION |
| `#learn` ou `#learning` | LEARNING |
| `#maint` ou `#maintenance` | MAINTENANCE |
| `#support` | SUPPORT |

#### 5.4.2 Timer Widget

Toujours visible dans la barre superieure de l'application. Affiche :
- Nom de la tache active et ticket(s) Jira
- Temps ecoule en temps reel (`HH:MM:SS`)
- Boutons : Pause / Resume / Stop
- Indicateur visuel de l'etat (couleur + animation pulsation pour actif)

En mode **Pomodoro**, affiche en plus :
- Numero de la session courante (ex: `Session 3/4`)
- Barre de progression du cycle
- Indication `WORK` / `BREAK` / `LONG BREAK`

#### 5.4.3 Task Card

Composant reutilisable pour afficher une tache :

```
+-------------------------------------------------------+
| [Couleur categorie] DPD-1423 Fix pagination    [>]    |
| #bugfix  |  Temps: 2h15m  |  Status: IN_PROGRESS      |
| Sous-taches: 2/3 terminees                             |
+-------------------------------------------------------+
```

- Click gauche : ouvre le detail de la tache
- Bouton play `[>]` : demarre/switch le timer
- Drag handle (a gauche) : pour reorganiser / deplacer entre jours
- Indicateur visuel si tache active (bordure verte pulsante)

#### 5.4.4 System Tray

Quand l'application est minimisee, elle reste dans le **system tray** (zone de notification) :

- **Icone** : change de couleur selon l'etat (vert = actif, jaune = pause, gris = inactif)
- **Tooltip** : affiche le timer courant et le nom de la tache
- **Menu clic droit** :
  - Ouvrir DevTrack
  - Pause / Resume
  - Stop session
  - Quitter

### 5.5 Navigation

| Ecran | Raccourci | Description |
|---|---|---|
| Aujourd'hui | `Ctrl+1` | Vue principale, taches du jour + timer |
| Backlog | `Ctrl+2` | Toutes les taches sans date |
| Timeline | `Ctrl+3` | Chronologie des sessions du jour |
| Calendrier | `Ctrl+4` | Vue semaine/mois avec densite de travail |
| Rapports | `Ctrl+5` | Generation d'exports |
| Parametres | `Ctrl+,` | Configuration de l'application |
| Command Palette | `Ctrl+K` | Actions rapides et recherche |

### 5.6 Etats visuels et feedback

| Action | Feedback |
|---|---|
| Demarrage timer | Bordure verte pulsante sur la task card, timer apparait dans la top bar |
| Pause timer | Couleur jaune, icone pause dans le tray |
| Arret timer | Notification OS : "Session terminee - DPD-1423 : 1h23m" |
| Creation tache | Animation d'insertion dans la liste, focus sur la nouvelle tache |
| Tache terminee | Animation de completion (checkmark), deplacement vers section "Done" |
| Session orpheline detectee | Modal non-bloquante avec 3 options de resolution |
| Inactivite detectee | Notification OS apres le seuil configurable |

### 5.7 Responsive et taille de fenetre

- **Taille minimale** : 900x600 px
- **Taille par defaut** : 1200x800 px
- **Sidebar collapsible** : en dessous de 1000px de largeur, la sidebar se reduit a des icones
- **Memorisation** : la taille et position de la fenetre sont sauvegardees entre les sessions

---

## 6. Fonctionnalites detaillees

### 6.1 Gestion des taches

#### F1.1 - Creation rapide

- Champ de saisie global accessible via `Ctrl+K`
- Parsing intelligent du titre :
  - Detection automatique des tickets Jira via regex `[A-Z]{2,10}-\d+`
  - Detection de la categorie via hashtag (`#bugfix`, `#dev`, etc.)
  - Tout le reste = titre de la tache
- **Objectif : creer une tache en < 3 secondes**

#### F1.2 - Organisation

- Drag & drop entre backlog, jours passes et jours futurs
- Multi-selection (Ctrl+Click / Shift+Click) pour planifier ou archiver en lot
- Tri et filtrage par categorie, statut, ticket Jira
- Edition inline du titre (double-click)

#### F1.3 - Sous-taches

- Creation de sous-taches depuis le detail d'une tache
- Indicateur de progression sur la tache parente (ex: "2/3 terminees")
- Le temps de la tache parente = somme des temps de ses sous-taches + son propre temps
- Profondeur limitee a 1 niveau

#### F1.4 - Templates

- Bibliotheque de taches recurrentes (Daily standup, Code review, Weekly report...)
- Instanciation via `/template <nom>` ou depuis l'interface
- Un template instancie cree une tache normale pour le jour courant

### 6.2 Timer et suivi du temps

#### F2.1 - Timer actif (mode libre)

- Boutons Start / Pause / Resume / Stop
- Une seule tache active a la fois ; switcher cloture automatiquement la session courante
- Timer visible en permanence dans la top bar + system tray
- Enregistrement des events (START, PAUSE, RESUME, END) avec timestamps

#### F2.2 - Mode Pomodoro

- Sessions de travail de duree configurable (defaut: 25 min)
- Pauses courtes (defaut: 5 min) et longue (defaut: 15 min apres 4 sessions)
- Notification OS a la fin de chaque periode
- Compteur de sessions Pomodoro completees par tache
- Le timer affiche le decompte restant (et non le temps ecoule)
- L'utilisateur choisit le mode (libre ou Pomodoro) au demarrage d'une session

#### F2.3 - Edition manuelle

- Modifier l'historique d'evenements d'une session a posteriori
- Ajouter une session passee manuellement (`source: MANUAL`)
- Interface de saisie : date, heure debut, heure fin, tache, notes

#### F2.4 - Calculs automatiques

- Temps total par tache, par ticket, par categorie, par jour, par semaine, par mois
- Conversion automatique configurable :
  - `>= hoursPerDay` (defaut 8h) = 1 jour
  - `>= halfDayThreshold` (defaut 4h) = 0.5 jour
- Temps effectif = exclusion des periodes de pause (calcul sur les events)

### 6.3 Detection Jira

#### F3.1 - Detection automatique

- Regex appliquee a la creation et modification du titre
- Pattern : `[A-Z]{2,10}-\d+`
- Plusieurs tickets possibles dans une meme tache
- Les tickets detectes sont stockes dans le champ `jiraTickets`

#### F3.2 - Agregation par ticket

- Vue consolidee par ticket : toutes les taches et sessions associees
- Temps total par ticket = somme de toutes les sessions liees
- Accessible depuis la vue Rapports et via la recherche dans la Command Palette

### 6.4 Vues principales

#### F4.1 - Vue Aujourd'hui (ecran principal)

- Taches planifiees pour le jour en cours
- Timer actif mis en avant (tache surlignee, temps en gros)
- Progression visible : temps total du jour, nombre de taches terminees
- Section "Done" pour les taches completees du jour
- Acces rapide au backlog en bas de l'ecran pour drag & drop

#### F4.2 - Vue Backlog

- Liste globale de toutes les taches sans `plannedDate`
- Filtrage par categorie et statut
- Tri par date de creation, categorie, ou alphabetique
- Actions en lot : planifier, archiver, supprimer

#### F4.3 - Timeline du jour

- Representation chronologique des sessions de la journee
- Visualisation des periodes de travail, pauses et inactivite
- Couleur codee par categorie
- Exportable en texte pour standup ou retrospective

#### F4.4 - Vue Calendrier / Semaine

- Grille semaine/mois avec densite de travail par jour (heatmap)
- Clic sur un jour : affiche les taches et sessions de ce jour
- Drag & drop de taches entre jours
- Vue du temps total par jour

#### F4.5 - Vue Rapports

- Interface de generation des differents exports
- Apercu avant export
- Selection de la periode (jour, semaine, mois)

### 6.5 Commandes rapides

Accessibles via la Command Palette (`Ctrl+K`) ou en prefixant la saisie avec `/` :

| Commande | Action |
|---|---|
| `/start <ticket ou titre>` | Cree et demarre immediatement une tache |
| `/pause` | Met en pause la session active |
| `/resume` | Reprend la session en pause |
| `/done` | Cloture la session et marque la tache done |
| `/switch <ticket ou titre>` | Stop session courante, start nouvelle |
| `/plan <ticket> today` | Planifie une tache du backlog a aujourd'hui |
| `/plan <ticket> tomorrow` | Planifie pour demain |
| `/plan <ticket> <date>` | Planifie pour une date specifique |
| `/template <nom>` | Instancie le template pour aujourd'hui |
| `/report today` | Genere l'export du jour |
| `/report week` | Genere le rapport hebdomadaire |
| `/report month` | Genere le CRA mensuel |
| `/pomodoro <ticket>` | Demarre une session Pomodoro sur la tache |

### 6.6 Gestion des sessions orphelines

#### Mecanisme 1 - Detection d'inactivite (seuil configurable, defaut 30 min)

1. L'application surveille l'activite (mouvement souris, frappe clavier dans l'app)
2. Apres le seuil : notification OS :
   > "Tu es inactif depuis X min sur DPD-1423. Toujours actif ?"
3. Options :
   - **Oui** : continue la session
   - **Pause automatique** : insere un event PAUSE retroactif
   - **Arreter la session** : cloture la session

#### Mecanisme 2 - Detection au redemarrage

1. Au demarrage, l'app detecte les sessions sans `endTime`
2. Modal :
   - **Cloturer a la derniere activite** : utilise le dernier event timestamp
   - **Cloturer maintenant** : endTime = now
   - **Editer manuellement** : ouvre l'editeur de session

### 6.7 Export et generation de rapports

#### F6.1 - Export du jour (Markdown / texte brut)

```markdown
# Rapport du 07/03/2026

## Tickets Jira
| Ticket    | Description        | Temps   |
|-----------|--------------------|---------|
| DPD-1423  | Fix pagination     | 2h15m   |
| DPD-2456  | Analyse cache      | 1h30m   |

## Taches hors-ticket
| Tache              | Categorie    | Temps  |
|--------------------|-------------|--------|
| Daily standup      | Reunion     | 0h30m  |
| Code review PR #42 | Review      | 0h45m  |

**Total : 5h00m**
```

#### F6.2 - Daily Standup genere automatiquement

```markdown
## Standup du 07/03/2026

### Hier j'ai travaille sur :
- DPD-1423 Fix pagination (2h15m)
- DPD-2456 Analyse cache (1h30m)
- Code review PR #42 (0h45m)

### Aujourd'hui je vais :
- DPD-1423 Fix pagination (continue)
- DPD-3789 Setup monitoring

### Blocages :
- (a remplir manuellement)
```

#### F6.3 - CRA mensuel

Tableau croise : tickets en lignes, jours du mois en colonnes. Valeurs en jours / demi-journees (0.5 / 1).

```
| Ticket   | Description          | 01  | 02  | 03  | ... | Total |
|----------|----------------------|-----|-----|-----|-----|-------|
| DPD-1423 | Fix pagination       | 1   |     | .5  | ... | 3j    |
| DPD-2456 | Analyse cache        |     | 1   | 1   | ... | 2j    |
| ---      | Reunions / annexes   | .5  | .5  |     | ... | 4j    |
```

- Export en **Markdown** et **copie dans le presse-papier**
- Ligne dediee aux taches sans ticket (reunions, maintenance...)
- Utilise les seuils configurables pour la conversion heures -> jours

---

## 7. Securite

### 7.1 Principes de securite

| Principe | Implementation |
|---|---|
| **Donnees locales uniquement** | Aucune connexion reseau, aucune API externe, aucune telemetrie |
| **Chiffrement au repos** | Base de donnees SQLite chiffree via SQLCipher |
| **Pas de secrets en clair** | La cle de chiffrement est derivee et stockee de maniere securisee |
| **Principe du moindre privilege** | L'application ne demande aucune permission systeme inutile |

### 7.2 Chiffrement de la base de donnees (SQLCipher)

- **Algorithme** : AES-256-CBC (standard SQLCipher)
- **Cle de chiffrement** : generee automatiquement au premier lancement
- **Stockage de la cle** : via les mecanismes natifs de l'OS
  - **Windows** : Windows Credential Manager (DPAPI)
  - **Linux** : libsecret (GNOME Keyring / KDE Wallet)
- **Impact performance** : negligeable pour les volumes de donnees de DevTrack (< 100k lignes)

### 7.3 Integrite des donnees

- **Transactions** : toutes les operations d'ecriture sont encapsulees dans des transactions Exposed
- **Contraintes FK** : integrite referentielle activee au niveau SQLite (`PRAGMA foreign_keys = ON`)
- **Migrations** : systeme de migration versionne pour les evolutions du schema
- **Validation** : validation des donnees en entree au niveau du domaine (pas uniquement en DB)

### 7.4 Backup et export

- **Export manuel** : l'utilisateur peut exporter une copie chiffree de la base de donnees
- **Format d'export** : fichier `.devtrack-backup` (SQLCipher DB + metadata)
- **Import** : restauration depuis un backup avec verification d'integrite
- **Pas de backup automatique** : l'utilisateur controle quand et ou sauvegarder

### 7.5 Logging et audit

- **Logging applicatif** via SLF4J + Logback
- **Niveaux** : ERROR, WARN, INFO, DEBUG (configurable)
- **Rotation** : fichiers de log rotatifs (taille max configurable, retention 30 jours par defaut)
- **Contenu** : actions utilisateur (creation tache, start/stop timer), erreurs, demarrage/arret de l'app
- **Pas de donnees sensibles dans les logs** : pas de contenu de taches, seulement les IDs et types d'action
- **Emplacement** : `~/.devtrack/logs/`

### 7.6 Securite du code

- **Dependances** : verification reguliere des vulnerabilites via Gradle dependency check
- **Typage strict** : Kotlin null-safety et types scelles pour les enums/etats
- **Pas d'injection SQL** : utilisation exclusive d'Exposed (requetes parametrees)
- **Validation d'entree** : tous les inputs utilisateur sont valides et sanitises

---

## 8. Exigences non-fonctionnelles

| Exigence | Critere | Mesure |
|---|---|---|
| **Performance demarrage** | < 2 secondes | Du clic a l'affichage de la vue Aujourd'hui |
| **Performance creation** | < 3 secondes | De l'ouverture de la command palette a la tache creee |
| **Performance timer** | Rafraichissement en temps reel | Mise a jour chaque seconde sans lag visible |
| **Disponibilite** | 100% offline | Fonctionne sans connexion reseau |
| **Taille app** | < 100 MB | Installeur avec JRE embarque |
| **Memoire** | < 300 MB RAM | En utilisation normale |
| **Portabilite** | Windows 10+ et Linux | Installeurs natifs pour chaque plateforme |
| **Maintenabilite** | Code Kotlin idiomatique | Tests unitaires sur la logique metier (>= 80% coverage domaine) |
| **Accessibilite** | Raccourcis clavier | Toutes les actions critiques accessibles au clavier |
| **Internationalisation** | FR + EN | Toute l'interface traduite, changement a chaud |
| **Fiabilite** | Pas de perte de donnees | Transactions, gestion des sessions orphelines, backup |

---

## 9. Priorisation et phases de developpement

### 9.1 MoSCoW

| Priorite | Fonctionnalites |
|---|---|
| **Must have** | Creation/edition de taches, timer Start/Pause/Stop, detection Jira, vue Aujourd'hui, export Markdown jour, theme Dark/Light, chiffrement SQLCipher |
| **Should have** | Backlog, 3 niveaux (Backlog/Planned/Active), commandes rapides (Ctrl+K), gestion sessions orphelines, CRA mensuel, sous-taches, i18n FR/EN |
| **Could have** | Templates, timeline du jour, daily standup genere, drag & drop, system tray widget, mode Pomodoro, vue calendrier |
| **Won't have (V1)** | Analyse productivite avancee, reconstruction git, integration Jira API, cloud sync, app mobile, collaboration multi-utilisateur, facturation |

### 9.2 Phases de developpement

#### Phase 1 - MVP (4-6 semaines)
> Objectif : application utilisable au quotidien

| Feature | Detail |
|---|---|
| Setup projet | Gradle, Compose Desktop, SQLite + SQLCipher + Exposed, structure de packages |
| Modele de domaine | Entites Task, WorkSession, SessionEvent, tables Exposed, migrations |
| CRUD Taches | Creation, edition, suppression, changement de statut |
| Timer libre | Start / Pause / Resume / Stop, une tache active a la fois |
| Vue Aujourd'hui | Liste des taches du jour, timer integre |
| Export jour | Generation Markdown du rapport quotidien |
| Theme | Material 3, Dark + Light mode |
| Logging | Setup SLF4J + Logback |

#### Phase 2 - Organisation (3-4 semaines)
> Objectif : confort d'utilisation et organisation

| Feature | Detail |
|---|---|
| Backlog | Vue backlog avec filtrage et tri |
| 3 niveaux de taches | Backlog / Planned / Active |
| Command Palette | Ctrl+K, commandes rapides, parsing intelligent |
| Sessions orphelines | Detection inactivite + detection au redemarrage |
| Sous-taches | Relation parent-enfant, calcul temps agrege |
| i18n | Support FR/EN complet |

#### Phase 3 - Reporting (3-4 semaines)
> Objectif : reporting complet et utile

| Feature | Detail |
|---|---|
| CRA mensuel | Tableau croise tickets/jours, export Markdown |
| CRA hebdomadaire | Rapport de la semaine |
| Daily standup | Generation automatique basee sur les sessions |
| Timeline du jour | Representation chronologique des sessions |
| Agregation par ticket | Vue consolidee par ticket Jira |

#### Phase 4 - Polish (3-4 semaines)
> Objectif : ergonomie et finitions

| Feature | Detail |
|---|---|
| Drag & drop | Reorganisation des taches, deplacement entre jours |
| Templates | Bibliotheque de taches recurrentes |
| System tray | Icone dans la zone de notification, menu contextuel |
| Mode Pomodoro | Timer Pomodoro configurable |
| Vue Calendrier | Vue semaine/mois avec heatmap |
| Parametres | Interface de configuration complete |
| Backup/Restore | Export et import de la base de donnees |

---

## 10. Hors-perimetre (V1)

Ces fonctionnalites sont **explicitement exclues** du scope V1 :

| Fonctionnalite exclue | Raison |
|---|---|
| Integration API Jira (lecture/ecriture) | Complexite, gestion tokens, dependance reseau |
| Synchronisation cloud ou multi-devices | Contraire au principe offline-first |
| Collaboration multi-utilisateurs | Scope personnel uniquement |
| Reconstruction de sessions depuis git log | Complexite, fiabilite incertaine |
| Application mobile | Hors cible (outil desktop pour dev) |
| Facturation ou gestion client | Hors scope fonctionnel |
| Support macOS | Ajoutable en Phase 5 si demande |
| Authentification au demarrage | Non souhaitee pour la V1 |
| Backup automatique | Export manuel suffisant pour la V1 |

---

## Annexe A - Raccourcis clavier

| Raccourci | Action |
|---|---|
| `Ctrl+K` | Ouvrir la Command Palette |
| `Ctrl+N` | Nouvelle tache |
| `Ctrl+1` | Vue Aujourd'hui |
| `Ctrl+2` | Vue Backlog |
| `Ctrl+3` | Vue Timeline |
| `Ctrl+4` | Vue Calendrier |
| `Ctrl+5` | Vue Rapports |
| `Ctrl+,` | Parametres |
| `Ctrl+S` | Start/Resume timer sur tache selectionnee |
| `Ctrl+P` | Pause timer actif |
| `Ctrl+Shift+S` | Stop timer actif |
| `Ctrl+D` | Marquer tache selectionnee comme done |
| `Ctrl+Backspace` | Archiver tache selectionnee |
| `Escape` | Fermer modale / Command Palette |

## Annexe B - Structure de la base de donnees

```sql
-- Tables principales (representees en SQL pour clarte, implementees via Exposed DSL)

CREATE TABLE tasks (
    id TEXT PRIMARY KEY,
    parent_id TEXT REFERENCES tasks(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL DEFAULT 'DEVELOPMENT',
    jira_tickets TEXT NOT NULL DEFAULT '[]',  -- JSON array
    status TEXT NOT NULL DEFAULT 'TODO',
    planned_date TEXT,  -- ISO date ou NULL
    is_template INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE work_sessions (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    date TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT,
    source TEXT NOT NULL DEFAULT 'TIMER',
    notes TEXT
);

CREATE TABLE session_events (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES work_sessions(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    timestamp TEXT NOT NULL
);

CREATE TABLE template_tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    default_duration_min INTEGER
);

CREATE TABLE user_settings (
    id TEXT PRIMARY KEY,
    locale TEXT NOT NULL DEFAULT 'fr',
    theme TEXT NOT NULL DEFAULT 'SYSTEM',
    inactivity_threshold_min INTEGER NOT NULL DEFAULT 30,
    hours_per_day REAL NOT NULL DEFAULT 8.0,
    half_day_threshold REAL NOT NULL DEFAULT 4.0,
    pomodoro_work_min INTEGER NOT NULL DEFAULT 25,
    pomodoro_break_min INTEGER NOT NULL DEFAULT 5,
    pomodoro_long_break_min INTEGER NOT NULL DEFAULT 15,
    pomodoro_sessions_before_long INTEGER NOT NULL DEFAULT 4
);

-- Index pour les requetes frequentes
CREATE INDEX idx_tasks_planned_date ON tasks(planned_date);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_parent_id ON tasks(parent_id);
CREATE INDEX idx_work_sessions_task_id ON work_sessions(task_id);
CREATE INDEX idx_work_sessions_date ON work_sessions(date);
CREATE INDEX idx_session_events_session_id ON session_events(session_id);
```
