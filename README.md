# Kandroid

Kandroid is an independent, open-source Android client for [Kanboard](https://kanboard.org/). It connects directly to a self-hosted Kanboard instance through the Kanboard JSON-RPC API.

Kandroid is not an official Kanboard application and is not affiliated with or endorsed by the Kanboard project.

## Features

- Connect to a Kanboard server with a username and personal API token.
- Browse projects and move between board columns by swiping.
- Create and edit tasks, including descriptions and due dates.
- Move and reorder tasks with controls or long-press drag gestures.
- Close, reopen, and delete tasks.
- Create projects and archive existing projects.
- Read cached projects and tasks while offline.
- Add configurable home-screen widgets for selected projects.
- Refresh boards and widgets from the Kanboard server.

## Requirements

- Android 8.0 (API 26) or newer
- A Kanboard server available over HTTPS
- A normal Kanboard user account and personal API token

The server address may be either the site root, such as `https://kanboard.example.com`, or its full `jsonrpc.php` endpoint.

## Build from source

The project requires JDK 17 and Android SDK 37. Open it in Android Studio and allow Gradle to sync, or build it from the command line.

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

On Linux or macOS:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Using Kandroid

On first launch, enter the HTTPS address of your Kanboard server, your username, and a personal API token. Test the connection, then save it.

- Tap the project name in the top bar to choose a board.
- Swipe horizontally to move between columns.
- Pull down to refresh the current board.
- Tap **+** to create a task.
- Tap a task to edit it, move it, set its due date, or close it.
- Hold and drag a task horizontally to move it to an adjacent column or vertically to reorder it.
- Open **Closed tasks** to reopen or permanently delete closed tasks.
- Add a Kandroid widget from the Android home-screen widget picker, then select the project it should display.

Moving a task into a column named Done does not close it automatically. Close it from the task details when required.

## Privacy and security

Kandroid does not include advertising, analytics, or tracking SDKs. It communicates directly with the Kanboard server selected by the user.

- Only HTTPS Kanboard endpoints are accepted.
- The username and API token are stored locally; the token is encrypted using Android Keystore.
- Project and task data is cached locally in a Room database to support offline viewing and widgets.
- Android backup is disabled for the application.
- No credentials, server addresses, or task data are included in this repository.

Users remain responsible for the security and privacy practices of the Kanboard server they connect to.

## Current limitations

- Offline access is read-only; changes are not queued for later synchronization.
- Dragging moves a task only to an adjacent column and does not provide continuous animated drag-and-drop.
- Archiving a project disables it in Kanboard; Kandroid does not delete projects.

## Contributing

Bug reports and pull requests are welcome. Please do not include real server addresses, credentials, or private Kanboard data in issues, logs, screenshots, test fixtures, or commits.

## License

Kandroid is licensed under the [GNU General Public License v3.0](LICENSE).

Kanboard is a separate project distributed under its own license. See the [Kanboard repository](https://github.com/kanboard/kanboard) for details.
