# TODO

## Both web UI and app
[ ] In the console list, only list the consoles that have games.
[ ] In the console list, add an image of the console.
[ ] BIOS files are appearing in the games list.
[ ] We should have dedicated path (in env variables) for BIOS files, so they can be separated from the ROMs.
[ ] Pass correct BIOS paths to libretro and EmulatorJS.
[ ] When autoscraping a game is done, the UI doesn't update, the game is stuck spinning.
[ ] Netplay game details doesn't respect box art aspect ratio difference between consoles.
[ ] NES box art aspect ratio could be more precise, compared to the box art.
[ ] When inviting to a Relay, the user should be able to select user from a list.
[ ] There need to be some kind of notifications, so that it is easy to see that you have been sent a Relay or multiplayer invite.

## Scripts
[ ] generate-secrets.sh should not overwrite .env, instead it should print the env content to stdout.

## Deploy
[ ] docker-compose.qa.yml should use variables for all paths, there should be no predefined paths in the yml.
[ ] Deploy documentation should mention required request headers and other details that are required for EmulatorJS to run at full speed.
