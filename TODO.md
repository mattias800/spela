# TODO

## Both web UI and app
[x] In the console list, only list the consoles that have games.
[x] In the console list, add an image of the console.
[x] BIOS files are appearing in the games list.
[x] We should have dedicated path (in env variables) for BIOS files, so they can be separated from the ROMs.
[x] Pass correct BIOS paths to libretro and EmulatorJS.
[x] When autoscraping a game is done, the UI doesn't automatically update, the game is stuck spinning until refresh browser. Ensure this logic works on desktop and app as well.
[x] Netplay game details doesn't respect box art aspect ratio difference between consoles.
[x] NES box art aspect ratio could be more precise, compared to the box art.
[ ] When inviting to a Relay, the user should be able to select user from a list.
[ ] There need to be some kind of notifications, so that it is easy to see that you have been sent a Relay or multiplayer invite.

## Android
[x] The "Delete" button in the server list has a broken icon

## Desktop
[x] Esc button on macOS does not quit the game.

## App and desktop
[ ] We have no strategy for database migrations in the app, the app could potentially break for users when upgrading. We have no releases yet, so this is not urgent.

## Scripts
[x] generate-secrets.sh should not overwrite .env, instead it should print the env content to stdout.

## Deploy
[x] docker-compose.qa.yml should use variables for all paths, there should be no predefined paths in the yml.
[ ] Deploy documentation should mention required request headers and other details that are required for EmulatorJS to run at full speed.
