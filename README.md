```
 ██████   ██████ 
██    ██ ██      
██    ██ ██      
██ ▄▄ ██ ██      
 ██████   ██████ 
    ▀▀         
```
Queue Commander is a Lanterna-based terminal UI (TUI) for browsing ActiveMQ queues and messages through Jolokia.

## What you can do
- List queues and current depth
- Switch queue manager at runtime
- Open a queue and list messages
- Open a message and view full message ID and body

## In-program usage
When the app is running:

- Arrow keys: move selection in lists
- Enter on a queue: open message list for that queue
- Enter on a message: open message details (ID + body)
- Q: open queue manager selection dialog
- R: refresh queue list for active queue manager
- Esc: close the current dialog, or exit the program from the main view

The main view also includes an inline queue manager selector at the top of the left panel.

## Requirements
- Java 17+
- Maven 3.8+

## Build
```bash
mvn clean package
```

This builds a shaded (fat) jar.

## Run
```bash
java -jar target/qc-1.0-SNAPSHOT.jar
```

## Configuration
Default configuration is read from:
- `src/main/resources/application.yaml` (packaged into the jar)

You can override config with an external file in one of these ways:

```bash
java -Dqc.config=/absolute/path/to/application.yaml -jar target/qc-1.0-SNAPSHOT.jar
```

```bash
export QC_CONFIG=/absolute/path/to/application.yaml
java -jar target/qc-1.0-SNAPSHOT.jar
```

If neither is set, the app also checks `./application.yaml` in the current working directory.

## application.yaml overview
`qc` supports:
- `active-qmanager`: default selected queue manager
- `ui.font-size`: terminal font size hint
- `ui.window.columns` and `ui.window.rows`: initial terminal size
- `ssl.truststore`: truststore settings for HTTPS brokers
- `qmanagers`: list of queue manager endpoints (`name`, `url`, `username`, `password`)

