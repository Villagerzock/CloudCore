#!/usr/bin/env python3
import json
import os
import sys
import time
import urllib.parse
import urllib.request
import urllib.error

GITLAB_URL = "https://git.bbcag.ch"
PROJECT_PATH = "inf-bl/st-gallen/2025/ssiebm/cloudcoreprojektantrag"

# Erstellt Issues anhand des Projektantrags.
# Voraussetzung:
#   export GITLAB_TOKEN="dein_gitlab_access_token"
#   python3 create_cloudcore_issues.py

ISSUES = [
	{
		"title": "User Story 1 – Proxy automatisch einrichten",
		"labels": ["user-story", "pflicht", "proxy"],
		"description": """## User Story

**Als** Administrator  
**möchte ich**, dass CloudCore beim Start automatisch einen Proxy einrichtet und startet,  
**damit** das Netzwerk ohne manuelle Konfiguration betriebsbereit ist.

## Akzeptanzkriterien

- [ ] CloudCore prüft beim Start, ob ein Proxy vorhanden ist.
- [ ] Falls kein Proxy vorhanden ist, wird automatisch eine Konfiguration erstellt.
- [ ] Der Proxy wird automatisch gestartet.
- [ ] Der Proxy ist für Spieler erreichbar.
- [ ] Fehler werden protokolliert.
"""
	},
	{
		"title": "User Story 2 – Server-Templates erstellen",
		"labels": ["user-story", "pflicht", "template"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** Server-Templates erstellen können,  
**damit** neue Serverinstanzen daraus erzeugt werden können.

## Akzeptanzkriterien

- [ ] Templates können erstellt werden.
- [ ] Templates enthalten die benötigten Serverdateien.
- [ ] Templates besitzen eine eigene Konfiguration.
- [ ] Templates werden in einer Liste angezeigt.
- [ ] Fehler werden protokolliert.
"""
	},
	{
		"title": "User Story 3 – Serverinstanzen starten",
		"labels": ["user-story", "pflicht", "server"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** Serverinstanzen aus Templates starten können,  
**damit** neue Minecraft-Server bereitgestellt werden können.

## Akzeptanzkriterien

- [ ] Beim Start wird eine Kopie des Templates erstellt.
- [ ] Die Serverinstanz wird gestartet.
- [ ] Die Instanz registriert sich beim Proxy.
- [ ] Die Instanz wird als laufend angezeigt.
- [ ] Spieler können auf die Instanz verbinden.
"""
	},
	{
		"title": "User Story 4 – Temporäre Serverinstanzen entfernen",
		"labels": ["user-story", "pflicht", "server"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** temporäre Serverinstanzen herunterfahren können,  
**damit** nicht benötigte Ressourcen freigegeben werden.

## Akzeptanzkriterien

- [ ] Instanzen können heruntergefahren werden.
- [ ] Die Instanz wird beim Proxy deregistriert.
- [ ] Die temporäre Serverkopie wird gelöscht.
- [ ] Das ursprüngliche Template bleibt erhalten.
- [ ] Die Instanz wird nicht mehr als laufend angezeigt.
"""
	},
	{
		"title": "User Story 5 – Singleton-Server verwenden",
		"labels": ["user-story", "pflicht", "singleton"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** Templates als Singleton-Server starten können,  
**damit** Änderungen dauerhaft gespeichert werden.

## Akzeptanzkriterien

- [ ] Templates können als Singleton gestartet werden.
- [ ] Es wird keine temporäre Kopie erstellt.
- [ ] Änderungen bleiben nach dem Stoppen erhalten.
- [ ] Eine Singleton-Instanz kann nicht mehrfach gleichzeitig gestartet werden.
- [ ] Singleton-Server werden als laufend angezeigt.
"""
	},
	{
		"title": "User Story 6 – Serverstatus anzeigen",
		"labels": ["user-story", "pflicht", "monitoring"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** den Status aller Server sehen können,  
**damit** ich das Netzwerk überwachen kann.

## Akzeptanzkriterien

- [ ] Alle Server werden angezeigt.
- [ ] Laufende und gestoppte Server sind unterscheidbar.
- [ ] Die Spieleranzahl wird angezeigt.
- [ ] Statusänderungen werden aktualisiert.
- [ ] Serverinformationen sind jederzeit abrufbar.
"""
	},
	{
		"title": "User Story 7 – Logs anzeigen",
		"labels": ["user-story", "pflicht", "logs"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** Serverlogs einsehen können,  
**damit** Fehler analysiert werden können.

## Akzeptanzkriterien

- [ ] Logs können geöffnet werden.
- [ ] Neue Logeinträge werden angezeigt.
- [ ] Fehler sind sichtbar.
- [ ] Der Log-Viewer kann beendet werden.
- [ ] Logs helfen bei der Fehleranalyse.
"""
	},
	{
		"title": "User Story 8 – Maintenance-Modus verwenden",
		"labels": ["user-story", "pflicht", "maintenance"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** einen Maintenance-Modus aktivieren können,  
**damit** während Wartungsarbeiten nur berechtigte Spieler Zugriff erhalten.

## Akzeptanzkriterien

- [ ] Maintenance kann aktiviert werden.
- [ ] Maintenance kann deaktiviert werden.
- [ ] Nicht berechtigte Spieler werden abgewiesen.
- [ ] Die MOTD zeigt den Wartungsstatus an.
- [ ] Berechtigte Spieler können weiterhin beitreten.
"""
	},
	{
		"title": "User Story 9 – Maintenance-Whitelist verwalten",
		"labels": ["user-story", "pflicht", "maintenance"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** Spieler zur Maintenance-Whitelist hinzufügen oder entfernen können,  
**damit** bestimmte Personen trotz Wartungsmodus Zugriff erhalten.

## Akzeptanzkriterien

- [ ] Spieler können per UUID hinzugefügt werden.
- [ ] Spieler können per Minecraft-Name hinzugefügt werden.
- [ ] Spielernamen werden automatisch in UUIDs umgewandelt.
- [ ] Spieler können entfernt werden.
- [ ] Die Whitelist bleibt gespeichert.
"""
	},
	{
		"title": "User Story 10 – Matchmaking verwenden",
		"labels": ["user-story", "pflicht", "matchmaking"],
		"description": """## User Story

**Als** Spieler  
**möchte ich** automatisch einem passenden Spielserver zugewiesen werden,  
**damit** ich keinen Server manuell auswählen muss.

## Akzeptanzkriterien

- [ ] Spieler können einer Queue beitreten.
- [ ] CloudCore sucht einen passenden Server.
- [ ] Falls nötig wird ein neuer Server gestartet.
- [ ] Spieler werden automatisch verbunden.
- [ ] Fehler werden behandelt.
"""
	},
	{
		"title": "User Story 11 – Lobby Load Balancing",
		"labels": ["user-story", "pflicht", "lobby"],
		"description": """## User Story

**Als** Spieler  
**möchte ich** automatisch einer geeigneten Lobby zugewiesen werden,  
**damit** die Spieler gleichmässig verteilt werden.

## Akzeptanzkriterien

- [ ] Beim Verbinden wird eine Lobby ausgewählt.
- [ ] Die aktuelle Auslastung wird berücksichtigt.
- [ ] Fehlerhafte Lobbys werden ignoriert.
- [ ] Spieler werden automatisch verbunden.
- [ ] Die Last wird möglichst gleichmässig verteilt.
"""
	},
	{
		"title": "User Story 12 – Weboberfläche",
		"labels": ["user-story", "optional", "web"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** CloudCore über eine Website bedienen können,  
**damit** kein direkter Konsolenzugriff erforderlich ist.

## Akzeptanzkriterien

- [ ] Die Weboberfläche ist über einen Browser erreichbar.
- [ ] Server können über die Weboberfläche angezeigt werden.
- [ ] Server können über die Weboberfläche gestartet und gestoppt werden.
- [ ] Logs können über die Weboberfläche eingesehen werden.
- [ ] Der Zugriff ist abgesichert.
"""
	},
	{
		"title": "User Story 13 – Paper-Plugin für Unterserver",
		"labels": ["user-story", "optional", "paper-plugin"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** ein Paper-Plugin verwenden können,  
**damit** Unterserver direkt mit CloudCore interagieren können.

## Akzeptanzkriterien

- [ ] Das Plugin kann auf Paper-Servern installiert werden.
- [ ] Das Plugin verbindet sich mit CloudCore oder dem Proxy-System.
- [ ] Das Plugin kann Spieler zu anderen Servern senden.
- [ ] Das Plugin kann für Serverwechsel-Funktionen genutzt werden.
- [ ] Fehler bei der Verbindung werden protokolliert.
"""
	},
	{
		"title": "User Story 14 – NPC-basierte Serverwechsel",
		"labels": ["user-story", "optional", "npc"],
		"description": """## User Story

**Als** Spieler  
**möchte ich** über NPCs zu anderen Servern wechseln können,  
**damit** die Navigation innerhalb des Netzwerks vereinfacht wird.

## Akzeptanzkriterien

- [ ] Ein NPC kann eine Serverwechsel-Aktion auslösen.
- [ ] Beim Interagieren mit dem NPC wird der Spieler zu einem Zielserver gesendet.
- [ ] Das Ziel kann ein konkreter Server oder ein Spielmodus sein.
- [ ] Falls kein Zielserver verfügbar ist, wird eine Fehlermeldung angezeigt.
- [ ] Die Funktion kann von bestehenden NPC-Plugins genutzt werden.
"""
	},
	{
		"title": "User Story 15 – Command-basierte Serverwechsel",
		"labels": ["user-story", "optional", "commands"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** Spieler per Command auf andere Server senden können,  
**damit** Serverwechsel unabhängig von NPCs möglich sind.

## Akzeptanzkriterien

- [ ] Es gibt einen Command zum Senden eines Spielers auf einen anderen Server.
- [ ] Der Command kann konkrete Servernamen verwenden.
- [ ] Der Command kann Spielmodi oder Matchmaking-Ziele verwenden.
- [ ] Fehlende Berechtigungen werden abgefangen.
- [ ] Ungültige Ziele erzeugen eine verständliche Fehlermeldung.
"""
	},
	{
		"title": "User Story 16 – Plugin-API",
		"labels": ["user-story", "optional", "api"],
		"description": """## User Story

**Als** Plugin-Entwickler  
**möchte ich** CloudCore-Funktionen über eine API verwenden können,  
**damit** andere Plugins Matchmaking oder Serverwechsel auslösen können.

## Akzeptanzkriterien

- [ ] Andere Plugins können CloudCore-Aktionen aufrufen.
- [ ] Eine Action kann einen Spieler zu einem Server senden.
- [ ] Eine Action kann einen Spieler in eine Matchmaking-Queue einreihen.
- [ ] Die API arbeitet mit serialisierbaren Daten wie Strings, Zahlen und Booleans.
- [ ] Fehler werden an das aufrufende Plugin zurückgegeben.
- [ ] Die API ist dokumentiert.
"""
	},
	{
		"title": "User Story 17 – Server-Metriken",
		"labels": ["user-story", "optional", "metrics"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** CPU-, RAM- und Netzwerkauslastung sehen können,  
**damit** ich die Auslastung überwachen kann.

## Akzeptanzkriterien

- [ ] Die RAM-Nutzung wird angezeigt.
- [ ] Die CPU-Auslastung wird angezeigt.
- [ ] Die Netzwerkauslastung wird angezeigt.
- [ ] Metriken werden regelmässig aktualisiert.
- [ ] Auffällige Werte können erkannt werden.
"""
	},
	{
		"title": "User Story 18 – Automatische Backups",
		"labels": ["user-story", "optional", "backup"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** automatische Backups von Templates und Singleton-Servern erstellen können,  
**damit** Datenverlust vermieden wird.

## Akzeptanzkriterien

- [ ] Backups können automatisch erstellt werden.
- [ ] Backups können manuell ausgelöst werden.
- [ ] Backups enthalten die relevanten Serverdateien.
- [ ] Backups können wiederhergestellt werden.
- [ ] Alte Backups können verwaltet oder gelöscht werden.
"""
	},
	{
		"title": "User Story 19 – Benutzer- und Rollenverwaltung",
		"labels": ["user-story", "optional", "roles"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** verschiedene Benutzerrollen verwalten können,  
**damit** Berechtigungen gezielt vergeben werden können.

## Akzeptanzkriterien

- [ ] Benutzer können erstellt werden.
- [ ] Rollen können vergeben werden.
- [ ] Berechtigungen können pro Rolle definiert werden.
- [ ] Kritische Aktionen sind nur mit ausreichender Berechtigung möglich.
- [ ] Benutzer können wieder entfernt werden.
"""
	},
	{
		"title": "User Story 20 – Automatische Skalierung",
		"labels": ["user-story", "optional", "scaling"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** Server automatisch starten und stoppen lassen können,  
**damit** Ressourcen effizient genutzt werden.

## Akzeptanzkriterien

- [ ] CloudCore erkennt, wenn zusätzliche Kapazität benötigt wird.
- [ ] CloudCore kann automatisch neue Serverinstanzen starten.
- [ ] Nicht mehr benötigte Instanzen können automatisch heruntergefahren werden.
- [ ] Mindest- und Maximalwerte können konfiguriert werden.
- [ ] Temporäre Instanzen werden nach dem Stoppen gelöscht.
"""
	},
	{
		"title": "User Story 21 – Unterstützung mehrerer Nodes",
		"labels": ["user-story", "optional", "node"],
		"description": """## User Story

**Als** Administrator  
**möchte ich** zusätzliche CloudCore-Nodes mit einer zentralen CloudCore-Instanz verbinden können,  
**damit** Minecraft-Server auf mehreren physischen Maschinen betrieben werden können.

## Akzeptanzkriterien

- [ ] Eine CloudCore-Node kann sich bei einer zentralen CloudCore-Instanz registrieren.
- [ ] Die zentrale Instanz kann alle verbundenen Nodes anzeigen.
- [ ] Server können auf einer bestimmten Node gestartet werden.
- [ ] Verfügbare Ressourcen der Nodes können berücksichtigt werden.
- [ ] Ausfälle von Nodes werden erkannt und angezeigt.
- [ ] Die Kommunikation erfolgt über eine definierte API.
"""
	}
]


def request(method: str, path: str, token: str, data: dict | None = None):
	url = f"{GITLAB_URL}/api/v4{path}"

	body = None
	headers = {
		"PRIVATE-TOKEN": token,
		"Accept": "application/json",
	}

	if data is not None:
		body = json.dumps(data).encode("utf-8")
		headers["Content-Type"] = "application/json"

	req = urllib.request.Request(
		url,
		data=body,
		headers=headers,
		method=method
	)

	try:
		with urllib.request.urlopen(req) as response:
			raw = response.read().decode("utf-8")
			return json.loads(raw) if raw else None
	except urllib.error.HTTPError as error:
		raw = error.read().decode("utf-8", errors="replace")
		print(f"HTTP {error.code} bei {method} {url}", file=sys.stderr)
		print(raw, file=sys.stderr)
		raise


def get_project(token: str):
	encoded_path = urllib.parse.quote(PROJECT_PATH, safe="")
	return request("GET", f"/projects/{encoded_path}", token)


def issue_exists(token: str, project_id: int, title: str) -> bool:
	encoded_title = urllib.parse.quote(title)
	result = request(
		"GET",
		f"/projects/{project_id}/issues?search={encoded_title}&in=title&state=all",
		token
	)

	for issue in result:
		if issue.get("title") == title:
			return True

	return False


def create_issue(token: str, project_id: int, issue: dict):
	payload = {
		"title": issue["title"],
		"description": issue["description"],
		"labels": ",".join(issue["labels"]),
	}

	return request("POST", f"/projects/{project_id}/issues", token, payload)


def main():
	token = os.getenv("GITLAB_TOKEN")

	if not token:
		print("Fehler: Environment Variable GITLAB_TOKEN ist nicht gesetzt.", file=sys.stderr)
		print('Beispiel: export GITLAB_TOKEN="dein_token"', file=sys.stderr)
		return 1

	print(f"Suche Projekt: {PROJECT_PATH}")
	project = get_project(token)
	project_id = project["id"]
	print(f"Projekt gefunden: {project['name']} (ID: {project_id})")

	created = 0
	skipped = 0

	for issue in ISSUES:
		title = issue["title"]

		if issue_exists(token, project_id, title):
			print(f"Übersprungen, existiert bereits: {title}")
			skipped += 1
			continue

		created_issue = create_issue(token, project_id, issue)
		print(f"Erstellt: #{created_issue['iid']} {title}")
		created += 1

		# kleine Pause, damit GitLab nicht unnötig gespammt wird
		time.sleep(0.2)

	print()
	print(f"Fertig. Erstellt: {created}, übersprungen: {skipped}")
	return 0


if __name__ == "__main__":
	raise SystemExit(main())
