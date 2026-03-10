# DevTrack - Plan d'Implementation

**Version:** 1.0
**Date:** Mars 2026
**Reference:** [PRD v2.0](./PRD.md)

---

## Conventions de suivi

- `[ ]` = A faire
- `[~]` = En cours
- `[x]` = Termine
- `[!]` = Bloque
- `[-]` = Annule

Chaque tache est identifiee par un code : `P<phase>.<groupe>.<numero>`
Exemple : `P0.1.3` = Phase 0, Groupe 1, Tache 3

---

## Phase 0 — Setup & Infrastructure

> **Objectif :** Projet compilable, lancable, avec toute l'infrastructure technique en place.
> Aucune fonctionnalite metier, mais tout le socle pour en developper.
> **Duree estimee :** 3-5 jours

### P0.1 — Initialisation du projet Gradle

- [ ] `P0.1.1` Creer le projet Gradle Kotlin DSL avec le plugin Compose Multiplatform
  - `build.gradle.kts` racine + `settings.gradle.kts`
  - Plugin `org.jetbrains.compose` + `org.jetbrains.kotlin.jvm`
  - Configuration du JDK cible (17+)
  - Configuration du packaging natif (`compose.desktop.nativeDistributions`)
- [ ] `P0.1.2` Ajouter toutes les dependances definies dans le PRD Section 2.2
  - Compose Desktop + Material 3
  - Exposed (core, dao, jdbc, java-time)
  - SQLite JDBC driver + SQLCipher
  - SLF4J + Logback
  - Koin (DI)
  - JUnit 5 + MockK + Compose UI Testing
  - kotlinx-serialization (pour JSON des jira_tickets)
  - kotlinx-coroutines
- [ ] `P0.1.3` Creer la structure de packages selon PRD Section 4.2
  ```
  src/main/kotlin/com/devtrack/
    app/
    ui/theme/
    ui/navigation/
    ui/components/
    ui/screens/today/
    ui/screens/backlog/
    ui/screens/timeline/
    ui/screens/calendar/
    ui/screens/settings/
    ui/screens/templates/
    ui/screens/reports/
    ui/i18n/
    viewmodel/
    domain/model/
    domain/usecase/
    domain/service/
    data/repository/
    data/repository/impl/
    data/database/
    infrastructure/systray/
    infrastructure/notification/
    infrastructure/export/
    infrastructure/logging/
    infrastructure/backup/
  src/main/resources/
  src/test/kotlin/com/devtrack/
  ```
- [ ] `P0.1.4` Creer le point d'entree `Main.kt` avec une fenetre Compose vide
  - Fenetre avec titre "DevTrack"
  - Taille par defaut 1200x800, minimum 900x600
  - Verification que l'app compile et se lance

### P0.2 — Base de donnees & Securite

- [ ] `P0.2.1` Configurer SQLCipher avec Exposed
  - Classe `DatabaseFactory` dans `data/database/`
  - Connexion SQLCipher avec cle chiffree
  - `PRAGMA foreign_keys = ON`
  - Chemin de la DB : `~/.devtrack/data/devtrack.db`
- [ ] `P0.2.2` Implementer le stockage securise de la cle de chiffrement
  - Interface `KeyStore` dans `infrastructure/`
  - Implementation Windows : DPAPI via `WindowsCredentialManager`
  - Implementation Linux : libsecret via `LinuxSecretService`
  - Detection automatique de la plateforme
  - Generation automatique de la cle au premier lancement (AES-256)
- [ ] `P0.2.3` Creer le systeme de migration de schema
  - Table `schema_version` pour tracker la version courante
  - Classe `MigrationManager` qui execute les migrations sequentiellement
  - Migration V1 : creation de toutes les tables (PRD Annexe B)
- [ ] `P0.2.4` Definir les tables Exposed DSL
  - `TasksTable` avec tous les champs (PRD 3.1)
  - `WorkSessionsTable` (PRD 3.2)
  - `SessionEventsTable` (PRD 3.3)
  - `TemplateTasksTable` (PRD 3.5)
  - `UserSettingsTable` (PRD 3.6)
  - Tous les index (PRD Annexe B)
- [ ] `P0.2.5` Ecrire les tests d'integration de la couche DB
  - Test de connexion SQLCipher
  - Test de creation des tables
  - Test des migrations
  - Test des contraintes FK (cascade delete)
  - Utiliser une DB en memoire pour les tests

### P0.3 — Logging

- [ ] `P0.3.1` Configurer Logback
  - Fichier `logback.xml` dans `src/main/resources/`
  - Appender console (pour dev) + appender fichier rotatif
  - Chemin des logs : `~/.devtrack/logs/devtrack.log`
  - Rotation : 10 MB par fichier, retention 30 jours
  - Pattern : `%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n`
- [ ] `P0.3.2` Creer un wrapper de logging pour l'audit
  - Classe `AuditLogger` dans `infrastructure/logging/`
  - Methodes : `logUserAction(action, entityType, entityId)`
  - Ne jamais logger le contenu des taches (titres, descriptions)
  - Seulement les IDs et les types d'action

### P0.4 — Injection de dependances (Koin)

- [ ] `P0.4.1` Configurer Koin
  - Module `databaseModule` : DatabaseFactory, migrations
  - Module `repositoryModule` : tous les repositories
  - Module `domainModule` : services, use cases
  - Module `viewModelModule` : tous les ViewModels
  - Module `infrastructureModule` : logging, notifications, export
- [ ] `P0.4.2` Initialiser Koin dans `Main.kt`
  - `startKoin { modules(...) }` avant le lancement de la fenetre Compose

### P0.5 — Internationalisation (fondation)

- [ ] `P0.5.1` Creer le systeme i18n
  - Fichier `messages_fr.properties` et `messages_en.properties` dans `resources/i18n/`
  - Objet `I18n` singleton avec `fun t(key: String): String`
  - Utilisation de `Locale` pour determiner la langue
  - Les cles seront ajoutees incrementalement a chaque ecran
- [ ] `P0.5.2` Ajouter les cles de base
  - Nom de l'app, labels de navigation, boutons communs (OK, Annuler, Sauvegarder...)

### P0.6 — Theme Material 3 (fondation)

- [ ] `P0.6.1` Creer le theme DevTrack
  - Fichier `Theme.kt` dans `ui/theme/`
  - Palette de couleurs Light (PRD 5.2) : Primary `#2563EB`, Surface `#FFFFFF`, etc.
  - Palette de couleurs Dark (PRD 5.2) : Primary `#60A5FA`, Surface `#1E1E2E`, etc.
  - Couleurs des categories (PRD 3.4) : `categoryColor(category: TaskCategory): Color`
  - Couleurs du timer : actif (vert), pause (jaune)
- [ ] `P0.6.2` Configurer la typographie
  - Integrer les fonts Inter/JetBrains Sans (titres) et JetBrains Mono (code/tickets)
  - Definir les styles Material 3 : displayLarge, headlineMedium, bodyLarge, labelSmall, etc.
- [ ] `P0.6.3` Implementer le switch de theme
  - Enum `ThemeMode { LIGHT, DARK, SYSTEM }`
  - Detection du theme systeme via `isSystemInDarkTheme()`
  - State reactif : changer le theme sans redemarrer l'app
- [ ] `P0.6.4` Tester visuellement le theme
  - Ecran de test temporaire avec les composants Material 3 de base
  - Verifier les deux themes (light/dark)
  - Verifier les couleurs des categories

### P0.7 — Navigation (fondation)

- [ ] `P0.7.1` Creer le systeme de navigation
  - Sealed class `Screen` : Today, Backlog, Timeline, Calendar, Reports, Settings, Templates
  - `NavigationState` avec `currentScreen: StateFlow<Screen>`
  - Composable `AppNavigation` qui switch le contenu principal selon l'ecran
- [ ] `P0.7.2` Creer le layout principal (shell)
  - Top bar avec logo, espace pour le timer widget, boutons
  - Sidebar avec les liens de navigation (icones + labels)
  - Zone de contenu principal
  - Status bar en bas
  - Sidebar collapsible sous 1000px de largeur (PRD 5.7)
- [ ] `P0.7.3` Implementer les raccourcis clavier de navigation
  - `Ctrl+1` a `Ctrl+5` pour les ecrans principaux (PRD Annexe A)
  - `Ctrl+,` pour les parametres
  - Gestionnaire de raccourcis global a la fenetre
- [ ] `P0.7.4` Memorisation de la taille/position de fenetre
  - Sauvegarder dans UserSettings ou un fichier de preferences
  - Restaurer au lancement

**Critere de validation Phase 0 :**
> L'application se lance, affiche le layout principal avec sidebar et navigation fonctionnelle,
> se connecte a une DB SQLCipher chiffree, les logs s'ecrivent dans `~/.devtrack/logs/`,
> le theme dark/light fonctionne, les raccourcis de navigation marchent.

---

## Phase 1 — MVP

> **Objectif :** Application utilisable au quotidien : creer des taches, tracker le temps, exporter le rapport du jour.
> **Duree estimee :** 4-6 semaines
> **Prerequis :** Phase 0 complete

### P1.1 — Modele de domaine (entites Kotlin)

- [ ] `P1.1.1` Creer les enums du domaine dans `domain/model/`
  - `TaskCategory` avec les 8 valeurs (PRD 3.4)
  - `TaskStatus` : TODO, IN_PROGRESS, PAUSED, DONE, ARCHIVED
  - `SessionSource` : TIMER, MANUAL, POMODORO
  - `EventType` : START, PAUSE, RESUME, END
  - `ThemeMode` : LIGHT, DARK, SYSTEM
- [ ] `P1.1.2` Creer les data classes du domaine
  - `Task` (PRD 3.1) — attention au `parentId` nullable, `jiraTickets` comme `List<String>`
  - `WorkSession` (PRD 3.2) — `endTime` nullable
  - `SessionEvent` (PRD 3.3)
  - `TemplateTask` (PRD 3.5)
  - `UserSettings` (PRD 3.6) avec toutes les valeurs par defaut
- [ ] `P1.1.3` Creer les data classes derivees (vues)
  - `TaskWithTime` : Task + duree totale calculee + nombre sous-taches + progression
  - `ActiveSessionState` : session en cours + events + duree courante
  - `DailyReport` : donnees pour l'export du jour
- [ ] `P1.1.4` Ecrire les tests unitaires des entites
  - Verification des valeurs par defaut
  - Serialisation/deserialisation des jiraTickets (JSON)

### P1.2 — Repositories (couche data)

- [ ] `P1.2.1` Definir les interfaces dans `data/repository/`
  - `TaskRepository` : CRUD + findByDate + findBacklog + findByJiraTicket + findByParentId
  - `WorkSessionRepository` : CRUD + findByTaskId + findByDate + findOrphans + findByDateRange
  - `SessionEventRepository` : CRUD + findBySessionId
  - `TemplateTaskRepository` : CRUD + findAll
  - `UserSettingsRepository` : get + save (singleton)
- [ ] `P1.2.2` Implementer `TaskRepositoryImpl` avec Exposed
  - Mapping TasksTable <-> Task
  - Gestion du JSON pour `jira_tickets` (kotlinx-serialization)
  - Requetes : par date, par statut, backlog (plannedDate IS NULL)
  - Toutes les operations dans des transactions
- [ ] `P1.2.3` Implementer `WorkSessionRepositoryImpl`
  - Mapping WorkSessionsTable <-> WorkSession
  - Requete findOrphans : `end_time IS NULL`
  - Requete findByDateRange pour les rapports
- [ ] `P1.2.4` Implementer `SessionEventRepositoryImpl`
  - Mapping SessionEventsTable <-> SessionEvent
  - Ordered by timestamp ASC
- [ ] `P1.2.5` Implementer `TemplateTaskRepositoryImpl`
- [ ] `P1.2.6` Implementer `UserSettingsRepositoryImpl`
  - Creer les settings par defaut au premier lancement s'ils n'existent pas (upsert)
- [ ] `P1.2.7` Ecrire les tests d'integration des repositories
  - Tests CRUD complets pour chaque repository
  - Tests des requetes specifiques (findByDate, findOrphans, etc.)
  - Tests de cascade delete (supprimer task -> sessions + events supprimes)
  - DB en memoire (SQLite sans SQLCipher pour les tests)

### P1.3 — Services metier (domain layer)

- [ ] `P1.3.1` Implementer `JiraTicketParser` dans `domain/service/`
  - Methode `extractTickets(title: String): List<String>`
  - Regex `[A-Z]{2,10}-\d+` (PRD 3.1)
  - Methode `extractCategory(title: String): TaskCategory?`
  - Parsing des hashtags `#bugfix`, `#dev`, etc. (PRD 5.4.1)
  - Methode `cleanTitle(title: String): String` — retire les hashtags du titre
  - Tests unitaires exhaustifs : titres avec 0, 1, N tickets, hashtags, edge cases
- [ ] `P1.3.2` Implementer `TimeCalculator` dans `domain/service/`
  - Methode `calculateEffectiveTime(events: List<SessionEvent>): Duration`
    - Somme des periodes START/RESUME -> PAUSE/END
    - Gestion des events manquants (session orpheline)
  - Methode `calculateTotalForTask(sessions: List<WorkSession>, events: Map<UUID, List<SessionEvent>>): Duration`
  - Methode `convertToDays(duration: Duration, settings: UserSettings): Double`
    - Conversion heures -> jours selon seuils configurables
  - Tests unitaires : sessions simples, avec pauses multiples, orphelines, edge cases (0 events, 1 event)
- [ ] `P1.3.3` Implementer `TaskService` dans `domain/service/`
  - `createTask(title: String, plannedDate: LocalDate?): Task`
    - Appelle JiraTicketParser pour extraire tickets et categorie
    - Genere UUID, timestamps
    - Sauvegarde via repository
    - Log via AuditLogger
  - `updateTask(task: Task): Task`
    - Re-parse les tickets Jira si le titre a change
    - Met a jour `updatedAt`
  - `deleteTask(id: UUID)`
    - Verification : stopper la session active si liee a cette tache
    - Cascade delete gere par la DB
  - `changeStatus(id: UUID, status: TaskStatus)`
  - `planTask(id: UUID, date: LocalDate)`
  - `unplanTask(id: UUID)` — remet en backlog
- [ ] `P1.3.4` Implementer `SessionService` dans `domain/service/`
  - `startSession(taskId: UUID, source: SessionSource): WorkSession`
    - Verifier s'il y a une session active -> la stopper d'abord (auto-switch)
    - Creer WorkSession + event START
    - Mettre la tache en IN_PROGRESS
  - `pauseSession(sessionId: UUID)`
    - Ajouter event PAUSE
    - Mettre la tache en PAUSED
  - `resumeSession(sessionId: UUID)`
    - Ajouter event RESUME
    - Remettre la tache en IN_PROGRESS
  - `stopSession(sessionId: UUID)`
    - Ajouter event END
    - Setter endTime sur la session
  - `getActiveSession(): ActiveSessionState?`
    - Chercher la session sans endTime
  - `switchToTask(taskId: UUID): WorkSession`
    - Stopper la session active + demarrer la nouvelle
  - Tests unitaires et d'integration : start/pause/resume/stop, switch, double start, etc.

### P1.4 — Vue Aujourd'hui (UI)

- [ ] `P1.4.1` Creer le `TodayViewModel`
  - State : `TodayUiState` (liste TaskWithTime, totalTimeToday, isLoading, error)
  - State : `activeSession: StateFlow<ActiveSessionState?>`
  - Actions : loadTasks, startTask, pauseSession, resumeSession, stopSession, markDone
  - Reactif : mise a jour du timer chaque seconde via coroutine `ticker`
- [ ] `P1.4.2` Creer le composant `TaskCard`
  - Affichage : indicateur couleur categorie (barre laterale), titre, tickets Jira en monospace, temps cumule, statut
  - Bouton play/pause inline
  - Indicateur visuel si tache active (bordure verte pulsante via `animateColor`)
  - Etat "done" : opacite reduite, checkmark
  - Click : ouvrir le detail (panneau lateral ou dialogue)
- [ ] `P1.4.3` Creer le composant `TimerWidget` (top bar)
  - Affichage : nom de la tache active, ticket Jira, temps `HH:MM:SS` en temps reel
  - Boutons : Pause / Resume / Stop (selon l'etat)
  - Couleur : vert si actif, jaune si en pause
  - Animation : pulsation douce quand actif
  - Etat vide : "Aucune session active" en gris
- [ ] `P1.4.4` Composer l'ecran `TodayScreen`
  - En-tete : date du jour, nombre de taches, temps total
  - Section "En cours" : tache active mise en avant
  - Section "A faire" : taches planifiees du jour (TODO, PAUSED)
  - Section "Terminees" : taches DONE du jour (collapsible)
  - Champ de creation rapide en haut (texte + Enter = creer une tache pour aujourd'hui)
  - Etat vide : illustration + message "Aucune tache planifiee pour aujourd'hui"
- [ ] `P1.4.5` Integrer le `TimerWidget` dans la top bar du layout principal
  - Visible sur tous les ecrans
  - Mis a jour en temps reel
- [ ] `P1.4.6` Creer le dialogue de detail/edition d'une tache
  - Champs editables : titre, description, categorie (dropdown), statut, date planifiee
  - Affichage : tickets Jira detectes (lecture seule), temps total, nombre de sessions
  - Boutons : Sauvegarder, Annuler, Supprimer (avec confirmation)
- [ ] `P1.4.7` Ecrire les tests
  - Tests unitaires du TodayViewModel (mock des repositories/services)
  - Tests de la logique de rafraichissement du timer
  - Tests UI Compose : rendu de TaskCard dans differents etats

### P1.5 — Export du jour

- [ ] `P1.5.1` Implementer `DailyReportGenerator` dans `infrastructure/export/`
  - Input : `date: LocalDate`
  - Recuperer toutes les sessions du jour avec leurs taches
  - Grouper par ticket Jira / hors-ticket
  - Calculer les temps avec TimeCalculator
  - Generer le Markdown selon le format PRD 6.7 F6.1
- [ ] `P1.5.2` Implementer la copie dans le presse-papier
  - Utiliser `java.awt.Toolkit.getDefaultToolkit().systemClipboard`
  - Copier le Markdown genere
  - Notification visuelle : "Rapport copie dans le presse-papier"
- [ ] `P1.5.3` Creer un composant UI de preview du rapport
  - Dialogue avec le Markdown rendu (ou brut dans un TextField readonly)
  - Bouton "Copier" + bouton "Exporter en fichier"
  - Selection du format (Markdown / texte brut)
- [ ] `P1.5.4` Integrer le bouton d'export dans la vue Aujourd'hui
  - Bouton dans le header ou la status bar
  - Raccourci `Ctrl+E` (ajout hors PRD, pratique)
- [ ] `P1.5.5` Ecrire les tests
  - Tests du DailyReportGenerator : jour avec sessions, jour vide, taches avec/sans tickets
  - Verification du format Markdown genere

### P1.6 — Status bar

- [ ] `P1.6.1` Creer le composant `StatusBar`
  - Barre en bas de la fenetre
  - Affichage : "Session active depuis Xh Xm" ou "Aucune session active"
  - Affichage : "Total aujourd'hui : Xh Xm"
  - Mis a jour en temps reel (observe le meme state que le timer)

### P1.7 — Polish MVP

- [ ] `P1.7.1` Gerer la fermeture propre de l'application
  - A la fermeture : si session active, ajouter event END + sauvegarder
  - Sauvegarder la taille/position de la fenetre
  - Flush les logs
- [ ] `P1.7.2` Gerer les erreurs de maniere gracieuse
  - Snackbar/Toast pour les erreurs non critiques
  - Dialogue d'erreur pour les erreurs critiques (DB corrompue, etc.)
  - Logging de toutes les erreurs
- [ ] `P1.7.3` Premier cycle de tests manuels complets
  - Scenario : creer 3 taches, demarrer/pauser/switcher le timer, exporter le rapport
  - Verifier les deux themes
  - Verifier la persistence apres redemarrage

**Critere de validation Phase 1 :**
> On peut creer des taches, les tracker avec le timer (start/pause/resume/stop/switch),
> voir le temps en temps reel, exporter le rapport du jour en Markdown.
> Les donnees persistent entre les sessions. Le theme dark/light fonctionne.

---

## Phase 2 — Organisation

> **Objectif :** Backlog, commande rapide, gestion des sessions orphelines, sous-taches, i18n complet.
> **Duree estimee :** 3-4 semaines
> **Prerequis :** Phase 1 complete

### P2.1 — Vue Backlog

- [ ] `P2.1.1` Creer le `BacklogViewModel`
  - State : liste de taches sans plannedDate, filtres actifs, tri courant
  - Actions : loadTasks, filterByCategory, filterByStatus, sortBy, planTask, archiveTask, deleteTasks
  - Selection multiple : Set<UUID> selectionnes
- [ ] `P2.1.2` Creer l'ecran `BacklogScreen`
  - Barre de filtres : chips de categories, dropdown de statut
  - Barre de tri : date creation, categorie, alphabetique
  - Liste de TaskCards (reutiliser le composant)
  - Multi-selection : checkbox sur chaque carte, barre d'actions en bas quand selection active
  - Actions en lot : "Planifier pour aujourd'hui", "Archiver", "Supprimer"
  - Champ de creation rapide (cree une tache sans date = backlog)
  - Etat vide : "Votre backlog est vide"
- [ ] `P2.1.3` Ajouter l'action "Envoyer au backlog" dans le detail d'une tache
  - Retirer la date planifiee -> la tache retourne au backlog
- [ ] `P2.1.4` Tests du BacklogViewModel

### P2.2 — Trois niveaux de taches

- [ ] `P2.2.1` Implementer la logique des 3 niveaux dans TaskService
  - **Backlog** : `plannedDate == null && status != ARCHIVED`
  - **Planned** : `plannedDate != null && status in (TODO, PAUSED, DONE)`
  - **Active** : `status == IN_PROGRESS` (session timer en cours)
  - Transition Backlog -> Planned : assigner une date
  - Transition Planned -> Active : demarrer le timer
- [ ] `P2.2.2` Ajouter un indicateur visuel du niveau dans TaskCard
  - Badge ou icone discret pour distinguer Backlog / Planned / Active
- [ ] `P2.2.3` Mettre a jour la vue Aujourd'hui
  - Section "Backlog" en bas de l'ecran avec les 5 premieres taches du backlog
  - Bouton "Planifier pour aujourd'hui" sur chaque tache du backlog visible

### P2.3 — Command Palette

- [ ] `P2.3.1` Creer le composant `CommandPalette`
  - Dialogue modal centre, apparait via `Ctrl+K`
  - Champ de saisie avec auto-focus
  - Liste de resultats filtres en temps reel
  - Navigation clavier : fleches haut/bas, Enter pour selectionner, Escape pour fermer
  - Deux modes :
    - **Mode commande** (saisie commence par `/`) : affiche les commandes disponibles
    - **Mode recherche** (sinon) : recherche dans les taches par titre / ticket
- [ ] `P2.3.2` Implementer le parsing intelligent de la saisie
  - Reutiliser `JiraTicketParser.extractTickets()` et `extractCategory()`
  - Si la saisie ne commence pas par `/` et ne match aucune tache existante -> proposer "Creer la tache: <titre>"
  - Parsing du hashtag pour la categorie (PRD 5.4.1)
- [ ] `P2.3.3` Implementer les commandes (PRD 6.5)
  - `/start <ticket ou titre>` : chercher la tache, la creer si inexistante, demarrer le timer
  - `/pause` : pause session active
  - `/resume` : resume session active
  - `/done` : stop session + marquer DONE
  - `/switch <ticket ou titre>` : stop + start sur nouvelle tache
  - `/plan <ticket> today|tomorrow|<date>` : planifier une tache
  - `/template <nom>` : instancier un template (stub pour Phase 4)
  - `/report today|week|month` : generer un rapport (week/month = stubs pour Phase 3)
  - `/pomodoro <ticket>` : stub pour Phase 4
- [ ] `P2.3.4` Implementer l'auto-completion
  - Suggestions de taches existantes pendant la saisie
  - Suggestions de commandes quand on tape `/`
  - Affichage de la description de chaque commande
- [ ] `P2.3.5` Ajouter le raccourci `Ctrl+N` comme alias pour ouvrir la palette en mode creation
- [ ] `P2.3.6` Tests unitaires du parsing de commandes + tests UI de la palette

### P2.4 — Gestion des sessions orphelines

- [ ] `P2.4.1` Implementer la detection au redemarrage
  - Au lancement de l'app, dans `SessionService.detectOrphanSessions(): List<WorkSession>`
  - Chercher les sessions avec `endTime == null`
  - Pour chaque orpheline : recuperer le dernier event timestamp
- [ ] `P2.4.2` Creer le dialogue `OrphanSessionDialog`
  - Modal au demarrage si orphelines detectees
  - Pour chaque session orpheline :
    - Affiche : tache, date, dernier timestamp
    - Options (PRD 6.6 Mecanisme 2) :
      - "Cloturer a la derniere activite" (endTime = dernier event)
      - "Cloturer maintenant" (endTime = now)
      - "Editer manuellement" (ouvre l'editeur de session)
  - Si plusieurs orphelines : les traiter une par une
- [ ] `P2.4.3` Implementer la detection d'inactivite
  - Tracker le dernier input utilisateur dans l'app (mouse move, key press)
  - Coroutine periodique qui verifie `now - lastActivity > threshold`
  - Seuil configurable via UserSettings.inactivityThresholdMin
- [ ] `P2.4.4` Creer la notification d'inactivite
  - Notification OS (PRD 6.6 Mecanisme 1) :
    > "Tu es inactif depuis X min sur <ticket>. Toujours actif ?"
  - Au clic sur la notification : ouvrir la fenetre avec les options
  - Options dans l'app : "Oui (continuer)" / "Pause automatique" / "Arreter la session"
  - "Pause automatique" : inserer un event PAUSE avec timestamp = `now - inactivityDuration`
- [ ] `P2.4.5` Tests : sessions orphelines, detection d'inactivite (avec mock du temps)

### P2.5 — Sous-taches

- [ ] `P2.5.1` Mettre a jour le TaskService pour gerer les sous-taches
  - `createSubTask(parentId: UUID, title: String): Task`
    - Verifier que le parent n'est pas deja une sous-tache (profondeur max 1)
    - Heriter la `plannedDate` du parent si non definie
  - `getSubTasks(parentId: UUID): List<Task>`
  - Mise a jour du calcul de temps : temps parent = ses sessions + sessions des sous-taches
- [ ] `P2.5.2` Mettre a jour le TaskCard pour afficher les sous-taches
  - Indicateur de progression : "2/3 terminees"
  - Expand/collapse pour voir la liste des sous-taches
  - Bouton "+" pour ajouter une sous-tache
- [ ] `P2.5.3` Mettre a jour le dialogue de detail de tache
  - Section "Sous-taches" avec liste, ajout, suppression
  - Indicateur de progression
  - Possibilite de demarrer le timer sur une sous-tache directement
- [ ] `P2.5.4` Tests : creation, profondeur limitee, calcul temps agrege, cascade delete

### P2.6 — Internationalisation complete

- [ ] `P2.6.1` Auditer tous les textes en dur dans l'UI
  - Parcourir tous les ecrans et composants
  - Remplacer chaque texte par un appel `I18n.t("key")`
- [ ] `P2.6.2` Completer les fichiers de traduction
  - `messages_fr.properties` : toutes les cles FR
  - `messages_en.properties` : toutes les cles EN
  - Organiser par ecran : `today.title`, `backlog.empty`, `timer.active`, etc.
- [ ] `P2.6.3` Implementer le changement de langue a chaud
  - Reactive via StateFlow : changer la locale -> re-render de l'UI
  - Sauvegarder la preference dans UserSettings
- [ ] `P2.6.4` Tester les deux langues sur tous les ecrans
  - Verifier qu'aucun texte n'est tronque
  - Verifier la coherence des traductions

### P2.7 — Edition manuelle de sessions

- [ ] `P2.7.1` Creer le dialogue `ManualSessionEditor`
  - Champs : tache (dropdown/search), date, heure debut, heure fin, notes
  - Validation : debut < fin, date coherente
  - Source = MANUAL
- [ ] `P2.7.2` Creer le dialogue `SessionEventEditor`
  - Liste des events d'une session existante
  - Modifier les timestamps des events
  - Ajouter/supprimer des events
  - Validation de la coherence (START avant PAUSE, etc.)
- [ ] `P2.7.3` Integrer dans l'UI
  - Bouton "Ajouter une session manuellement" dans la vue Aujourd'hui
  - Bouton "Editer" sur chaque session dans le detail d'une tache
- [ ] `P2.7.4` Tests de validation des sessions manuelles

**Critere de validation Phase 2 :**
> La command palette fonctionne (Ctrl+K), les commandes rapides creent/demarre/pause/stop les taches.
> Le backlog est gere, les sessions orphelines sont detectees au redemarrage et par inactivite.
> Les sous-taches fonctionnent. L'interface est disponible en FR et EN.

---

## Phase 3 — Reporting

> **Objectif :** CRA mensuel, hebdomadaire, standup genere, timeline, agregation par ticket.
> **Duree estimee :** 3-4 semaines
> **Prerequis :** Phase 2 complete

### P3.1 — Infrastructure de reporting

- [ ] `P3.1.1` Creer l'interface `ReportGenerator` dans `infrastructure/export/`
  - Methode `generate(period: ReportPeriod): ReportOutput`
  - `ReportPeriod` : sealed class (Day, Week, Month) avec dates
  - `ReportOutput` : `markdownContent: String`, `plainTextContent: String`, `title: String`
- [ ] `P3.1.2` Creer les services d'agregation dans `domain/service/`
  - `ReportDataService`
    - `getDailyData(date: LocalDate): DailyReportData`
    - `getWeeklyData(weekStart: LocalDate): WeeklyReportData`
    - `getMonthlyData(year: Int, month: Int): MonthlyReportData`
  - Structures de donnees pour chaque type de rapport
  - Agregation par ticket, par categorie, par jour

### P3.2 — CRA mensuel

- [ ] `P3.2.1` Implementer `MonthlyReportGenerator`
  - Tableau croise : tickets en lignes, jours du mois en colonnes (PRD 6.7 F6.3)
  - Valeurs en jours / demi-journees selon les seuils configurables
  - Ligne "Reunions / annexes" pour les taches sans ticket Jira
  - Colonne "Total" par ticket
  - Ligne "Total" par jour
- [ ] `P3.2.2` Gerer la conversion heures -> jours
  - Utiliser `TimeCalculator.convertToDays()` avec les seuils de UserSettings
  - Arrondir a 0.5 pres : 0, 0.5, 1
- [ ] `P3.2.3` Tests du CRA mensuel
  - Mois avec donnees variees, mois vide, taches sans tickets, taches avec multiples tickets

### P3.3 — CRA hebdomadaire

- [ ] `P3.3.1` Implementer `WeeklyReportGenerator`
  - Format similaire au rapport quotidien mais sur la semaine (lundi a vendredi)
  - Tableau : tickets, description, temps par jour de la semaine, total
  - Section taches hors-ticket
- [ ] `P3.3.2` Tests du rapport hebdomadaire

### P3.4 — Daily Standup genere

- [ ] `P3.4.1` Implementer `StandupGenerator`
  - Section "Hier j'ai travaille sur" : sessions de la veille (PRD 6.7 F6.2)
  - Section "Aujourd'hui je vais" : taches planifiees pour aujourd'hui
  - Section "Blocages" : placeholder a remplir manuellement
  - Gestion du lundi : "vendredi dernier" au lieu de "hier"
- [ ] `P3.4.2` Tests du standup : jour normal, lundi, jour sans sessions precedentes

### P3.5 — Vue Rapports (ecran)

- [ ] `P3.5.1` Creer le `ReportsViewModel`
  - State : type de rapport selectionne, periode, preview du rapport, isGenerating
  - Actions : selectPeriod, generateReport, copyToClipboard, exportToFile
- [ ] `P3.5.2` Creer l'ecran `ReportsScreen`
  - Selecteur de type : Jour / Semaine / Mois / Standup
  - Selecteur de periode : date picker (jour), semaine picker, mois picker
  - Zone de preview : rendu du Markdown
  - Boutons : "Copier dans le presse-papier", "Exporter en fichier .md"
  - Feedback : notification "Copie !" avec animation
- [ ] `P3.5.3` Brancher les commandes `/report today|week|month` de la Command Palette
  - Naviguer vers l'ecran Rapports avec le type et la periode pre-selectionnes

### P3.6 — Timeline du jour

- [ ] `P3.6.1` Creer le `TimelineViewModel`
  - State : date selectionnee, sessions du jour ordonnees, totalTime
  - Calcul des gaps (periodes sans session) entre les sessions
- [ ] `P3.6.2` Creer le composant `TimelineBar`
  - Axe horizontal = heures de la journee (8h -> 20h par defaut)
  - Blocs colores par categorie pour chaque session
  - Gaps en gris clair pour les periodes d'inactivite
  - Indicateurs de pause (hachures ou pointilles)
  - Tooltip au survol : nom de la tache, duree
- [ ] `P3.6.3` Creer l'ecran `TimelineScreen`
  - Timeline visuelle en haut
  - Liste detaillee des sessions en dessous (heure debut, fin, duree, tache)
  - Navigation entre jours (fleches gauche/droite, date picker)
  - Bouton "Exporter" (texte brut de la timeline)
- [ ] `P3.6.4` Tests du TimelineViewModel

### P3.7 — Agregation par ticket Jira

- [ ] `P3.7.1` Implementer `JiraAggregationService` dans `domain/service/`
  - `getTicketSummary(ticket: String): TicketSummary`
    - Toutes les taches liees a ce ticket
    - Toutes les sessions liees (directes + via taches)
    - Temps total
    - Jours travailles
  - `getAllTickets(): List<TicketSummary>` — tous les tickets connus, tries par temps
- [ ] `P3.7.2` Ajouter une section "Par ticket" dans l'ecran Rapports
  - Liste des tickets avec temps total
  - Clic sur un ticket : detail des taches et sessions
- [ ] `P3.7.3` Ajouter la recherche par ticket dans la Command Palette
  - Taper un ticket Jira -> afficher le resume (temps total, derniere session)

**Critere de validation Phase 3 :**
> Le CRA mensuel genere un tableau croise correct. Le rapport hebdomadaire et le standup
> sont generes. La timeline affiche les sessions du jour visuellement. L'agregation par
> ticket Jira fonctionne.

---

## Phase 4 — Polish & Ergonomie

> **Objectif :** Drag & drop, templates, system tray, Pomodoro, calendrier, parametres, backup.
> **Duree estimee :** 3-4 semaines
> **Prerequis :** Phase 3 complete

### P4.1 — Drag & Drop

- [ ] `P4.1.1` Implementer le drag & drop dans la vue Aujourd'hui
  - Reorganiser l'ordre des taches du jour (ordre d'affichage)
  - Drag depuis la section "Backlog" en bas vers la liste du jour = planifier
- [ ] `P4.1.2` Implementer le drag & drop dans la vue Calendrier (P4.5)
  - Deplacer une tache d'un jour a un autre
  - Drag depuis le backlog vers un jour du calendrier
- [ ] `P4.1.3` Feedback visuel du drag & drop
  - Indicateur de zone de drop (surlignage)
  - Ghost element pendant le drag
  - Animation de repositionnement
- [ ] `P4.1.4` Tests du drag & drop (integration)

### P4.2 — Templates

- [ ] `P4.2.1` Creer le `TemplatesViewModel`
  - CRUD des templates
  - Action : instancier un template pour un jour donne
- [ ] `P4.2.2` Creer l'ecran `TemplatesScreen`
  - Liste des templates avec categorie et duree par defaut
  - Formulaire d'ajout/edition : titre, categorie, duree estimee
  - Bouton "Instancier pour aujourd'hui" sur chaque template
  - Suppression avec confirmation
- [ ] `P4.2.3` Creer des templates par defaut au premier lancement
  - "Daily standup" (MEETING, 15 min)
  - "Code review" (REVIEW, 30 min)
  - "Weekly report" (DOCUMENTATION, 30 min)
- [ ] `P4.2.4` Brancher la commande `/template <nom>` dans la Command Palette
  - Auto-completion avec les noms de templates existants
  - Instancier = creer une tache normale pour aujourd'hui avec les valeurs du template
- [ ] `P4.2.5` Tests des templates

### P4.3 — System Tray

- [ ] `P4.3.1` Implementer l'integration system tray
  - Utiliser `java.awt.SystemTray` et `java.awt.TrayIcon`
  - Icone qui change de couleur : vert (actif), jaune (pause), gris (inactif)
  - Tooltip : "[Timer] DPD-1423 - 01:23:45" ou "DevTrack - Inactif"
- [ ] `P4.3.2` Implementer le menu contextuel du tray
  - "Ouvrir DevTrack" : ramener la fenetre au premier plan
  - "Pause" / "Resume" : selon l'etat du timer
  - "Stop session" : arreter le timer
  - Separateur
  - "Quitter" : fermer l'application proprement
- [ ] `P4.3.3` Comportement de minimisation
  - Fermer la fenetre (X) = minimiser dans le tray (pas quitter)
  - "Quitter" dans le tray = fermer reellement l'application
  - Double-clic sur l'icone tray = ouvrir la fenetre
- [ ] `P4.3.4` Mise a jour en temps reel de l'icone et du tooltip
  - Coroutine qui met a jour le tooltip avec le timer chaque seconde
  - Changement d'icone instantane lors des transitions start/pause/stop
- [ ] `P4.3.5` Tests manuels du system tray (Windows + Linux)

### P4.4 — Mode Pomodoro

- [ ] `P4.4.1` Creer le `PomodoroService` dans `domain/service/`
  - Gestion du cycle : WORK -> BREAK -> WORK -> ... -> LONG_BREAK
  - Compteur de sessions completees
  - Configuration via UserSettings (work/break/longBreak/sessionsBeforeLong)
  - Timer decompte (temps restant, pas temps ecoule)
  - Transitions automatiques : fin de work -> notification -> pause
  - Enregistrement des events dans SessionEvent normalement
- [ ] `P4.4.2` Mettre a jour le `TimerWidget` pour le mode Pomodoro
  - Afficher le decompte restant au lieu du temps ecoule
  - Indicateur de phase : "WORK" / "BREAK" / "LONG BREAK"
  - Numero de session : "Session 3/4"
  - Barre de progression du cycle complet
  - Couleur specifique par phase
- [ ] `P4.4.3` Implementer les notifications Pomodoro
  - Notification OS a la fin de chaque phase :
    - Fin work : "Pause ! Prends 5 min."
    - Fin break : "C'est reparti ! Session X/4"
    - Fin long break : "Nouveau cycle Pomodoro"
  - Son de notification (optionnel, configurable)
- [ ] `P4.4.4` Ajouter le choix du mode au demarrage d'une session
  - Quand on clique "Start" sur une tache : popup rapide "Timer libre / Pomodoro"
  - Ou via la commande `/pomodoro <ticket>`
- [ ] `P4.4.5` Tests du PomodoroService : cycle complet, interruption, configuration custom

### P4.5 — Vue Calendrier

- [ ] `P4.5.1` Creer le `CalendarViewModel`
  - State : mois/semaine affiche, donnees de densite par jour, taches du jour selectionne
  - Actions : naviguer mois/semaine, selectionner un jour, planifier tache
- [ ] `P4.5.2` Creer le composant `CalendarGrid`
  - Grille mensuelle avec les jours
  - Heatmap de densite de travail par jour (intensite de couleur selon les heures)
  - Indicateur du nombre de taches par jour
  - Jour courant surligne
  - Clic sur un jour : afficher les taches dans un panneau lateral
- [ ] `P4.5.3` Creer la vue semaine alternative
  - 5 colonnes (lundi a vendredi)
  - Liste des taches dans chaque jour
  - Temps total par jour
  - Support du drag & drop entre jours (P4.1.2)
- [ ] `P4.5.4` Creer l'ecran `CalendarScreen`
  - Toggle vue mois / vue semaine
  - Navigation : mois precedent/suivant, "Aujourd'hui"
  - Panneau lateral : detail du jour selectionne
- [ ] `P4.5.5` Tests du CalendarViewModel

### P4.6 — Ecran Parametres

- [ ] `P4.6.1` Creer le `SettingsViewModel`
  - Charger les UserSettings au demarrage
  - Sauvegarder chaque modification immediatement
  - Actions pour chaque parametre
- [ ] `P4.6.2` Creer l'ecran `SettingsScreen`
  - **Section Apparence**
    - Theme : Light / Dark / System (radio buttons)
    - Langue : Francais / English (dropdown)
  - **Section Timer**
    - Seuil d'inactivite (slider, 5-120 min, defaut 30)
  - **Section Pomodoro**
    - Duree travail (slider, 15-60 min, defaut 25)
    - Duree pause (slider, 1-15 min, defaut 5)
    - Duree pause longue (slider, 5-30 min, defaut 15)
    - Sessions avant pause longue (1-8, defaut 4)
  - **Section Rapports**
    - Heures par jour (input numerique, defaut 8.0)
    - Seuil demi-journee (input numerique, defaut 4.0)
  - **Section Donnees**
    - Bouton "Exporter un backup"
    - Bouton "Importer un backup"
    - Bouton "Exporter toutes les donnees en Markdown"
    - Chemin de la base de donnees (lecture seule)
    - Chemin des logs (lecture seule)
  - **Section A propos**
    - Version de l'application
    - Licences des dependances
- [ ] `P4.6.3` Appliquer les changements en temps reel
  - Changement de theme : immediat
  - Changement de langue : immediat (reactif via i18n StateFlow)
  - Changement de seuil d'inactivite : pris en compte au prochain cycle
- [ ] `P4.6.4` Tests du SettingsViewModel

### P4.7 — Backup / Restore

- [ ] `P4.7.1` Implementer `BackupService` dans `infrastructure/backup/`
  - `exportBackup(destination: Path): BackupResult`
    - Copier la DB SQLCipher dans le fichier destination
    - Ajouter un fichier de metadata (version app, date export, version schema)
    - Format : `.devtrack-backup` (ZIP contenant db + metadata.json)
  - `importBackup(source: Path): ImportResult`
    - Verifier l'integrite du backup (metadata, version compatible)
    - Fermer la connexion DB courante
    - Remplacer la DB par celle du backup
    - Rouvrir la connexion
    - Retourner le resultat (succes, nb taches/sessions importees)
- [ ] `P4.7.2` Integrer dans l'ecran Parametres
  - Bouton "Exporter" -> dialogue de choix de fichier (SaveDialog)
  - Bouton "Importer" -> dialogue de choix de fichier (OpenDialog) + confirmation
  - Feedback : "Backup exporte avec succes" / "Donnees restaurees (X taches, Y sessions)"
- [ ] `P4.7.3` Tests du BackupService
  - Export + re-import -> verification que les donnees sont identiques
  - Import d'un backup corrompu -> erreur gracieuse
  - Import d'une version incompatible -> erreur avec message

### P4.8 — Notifications OS

- [ ] `P4.8.1` Creer `NotificationService` dans `infrastructure/notification/`
  - Interface commune pour Windows et Linux
  - Implementation Windows : `java.awt.TrayIcon.displayMessage()` ou WinAPI
  - Implementation Linux : `notify-send` via ProcessBuilder ou DBus
  - Methodes :
    - `notify(title: String, message: String, type: NotificationType)`
    - Types : INFO, WARNING, TIMER_END, INACTIVITY
- [ ] `P4.8.2` Brancher les notifications sur les evenements
  - Fin de session : "Session terminee - DPD-1423 : 1h23m"
  - Inactivite : "Tu es inactif depuis X min..."
  - Pomodoro : fin de phase (work/break/long break)
- [ ] `P4.8.3` Tests manuels sur Windows et Linux

### P4.9 — Packaging & Distribution

- [ ] `P4.9.1` Configurer le packaging Compose Desktop
  - `compose.desktop.nativeDistributions` dans `build.gradle.kts`
  - Nom : "DevTrack"
  - Icone de l'application (creer ou sourcer une icone)
  - Version
  - JDK embarque (JRE minimal via jlink)
- [ ] `P4.9.2` Build Windows
  - Generer `.msi` et/ou `.exe`
  - Tester l'installation sur Windows 10/11
  - Verifier : icone, raccourci bureau, desinstallation propre
- [ ] `P4.9.3` Build Linux
  - Generer `.deb` et `.AppImage`
  - Tester l'installation sur Ubuntu et Fedora
  - Verifier : icone, lanceur dans le menu, desinstallation propre
- [ ] `P4.9.4` Verifier la taille de l'installeur (< 100 MB, PRD 8)
- [ ] `P4.9.5` Verifier le demarrage < 2 secondes sur les deux plateformes

**Critere de validation Phase 4 :**
> Drag & drop fonctionnel, templates instanciables, system tray avec icone reactive,
> mode Pomodoro complet, vue calendrier, parametres configurables, backup/restore,
> notifications OS, packaging natif pour Windows et Linux.

---

## Phase 5 — Tests finaux & Stabilisation

> **Objectif :** Couverture de tests, performance, bugs, documentation interne.
> **Duree estimee :** 1-2 semaines
> **Prerequis :** Phase 4 complete

### P5.1 — Couverture de tests

- [ ] `P5.1.1` Verifier la couverture de tests sur la couche domain (objectif >= 80%)
  - TimeCalculator, JiraTicketParser, TaskService, SessionService, PomodoroService
  - ReportDataService, JiraAggregationService
- [ ] `P5.1.2` Ajouter les tests manquants sur les repositories
- [ ] `P5.1.3` Ajouter des tests UI Compose pour les composants critiques
  - TaskCard, TimerWidget, CommandPalette
- [ ] `P5.1.4` Ecrire des tests d'integration end-to-end
  - Scenario complet : creer tache -> start -> pause -> resume -> stop -> export

### P5.2 — Performance

- [ ] `P5.2.1` Mesurer le temps de demarrage et optimiser si > 2s
  - Lazy loading des ecrans non visibles
  - Initialisation asynchrone de la DB
- [ ] `P5.2.2` Profiler la memoire en utilisation normale (objectif < 300 MB)
- [ ] `P5.2.3` Tester avec un volume de donnees realiste
  - 500+ taches, 2000+ sessions, 6 mois de donnees
  - Verifier que les requetes restent rapides
  - Optimiser les index si necessaire
- [ ] `P5.2.4` Verifier l'absence de fuites memoire
  - Laisser l'app tourner 8h avec un timer actif
  - Surveiller la consommation memoire

### P5.3 — Securite finale

- [ ] `P5.3.1` Audit de securite des dependances
  - Gradle dependency check pour les CVE connues
  - Mettre a jour les dependances vulnerables
- [ ] `P5.3.2` Verifier qu'aucune donnee sensible n'est dans les logs
  - Parcourir tous les appels de log
  - Verifier : pas de titres de taches, pas de descriptions, pas de cle DB
- [ ] `P5.3.3` Verifier le chiffrement de la DB en production
  - Tenter d'ouvrir le fichier .db avec un outil SQLite standard -> doit echouer
  - Verifier que la cle est bien dans le credential manager OS
- [ ] `P5.3.4` Verifier la validation des entrees
  - Tester les injections dans les champs de saisie
  - Verifier les limites (titres tres longs, caracteres speciaux, unicode, emojis)

### P5.4 — Bug fixes & polish final

- [ ] `P5.4.1` Session de tests exploratoires
  - Tester tous les workflows du PRD 3.1 (workflow quotidien)
  - Tester les edge cases : fermeture brutale, perte de focus, resize fenetre
  - Tester les deux themes sur tous les ecrans
  - Tester les deux langues sur tous les ecrans
- [ ] `P5.4.2` Corriger tous les bugs trouves
- [ ] `P5.4.3` Verifier la coherence visuelle
  - Espacement, alignement, couleurs sur tous les ecrans
  - Verification Dark mode : contraste, lisibilite
  - Verification Light mode : contraste, lisibilite

---

## Resume des dependances entre phases

```
Phase 0 (Setup)
  |
  v
Phase 1 (MVP) ------> Utilisable au quotidien
  |
  v
Phase 2 (Organisation) --> Confortable et organise
  |
  v
Phase 3 (Reporting) ----> Rapports complets
  |
  v
Phase 4 (Polish) -------> Application finie
  |
  v
Phase 5 (Stabilisation) -> Prete pour distribution
```

## Metriques de suivi

| Phase | Nb taches | Objectif duree |
|-------|-----------|----------------|
| Phase 0 | 22 | 3-5 jours |
| Phase 1 | 27 | 4-6 semaines |
| Phase 2 | 26 | 3-4 semaines |
| Phase 3 | 16 | 3-4 semaines |
| Phase 4 | 30 | 3-4 semaines |
| Phase 5 | 12 | 1-2 semaines |
| **Total** | **133** | **~15-21 semaines** |
