# Nudge

The app shows edge pops when you press the volume or power buttons and includes a volume slider based on a concept design that has now become a reality.

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/DarkAspect1/Nudge">
    <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="56">
  </a>
</p>

![Nudge](assets/Nudge.png)

## Features

- **Edge pops:** on volume and power button presses.
  Customize its width, height, position, and timeout for each button

- **Custom volume slider:** inspired by a concept design that was originally made for Iphones has now become a reality for Androids LOL.
  Customize its width, height, position, corners rounding, edge inset, and timeout, with an option for floating.

### Actions Tab
- **DND/Silent panel:** change between two modes either silent and normal or dnd on/off. customization over width, height, position, timeout, even the unfocused icon placement, and selected side you want in both portrait and landscape mode.
- **Modes/Actions panel:** same customization as DND/Silent panel. add new custom modes with custom icons/colors/names or even edit the existing ones and change their order like how you want it to be! **How it works:** it sends a quick silent notification that can be used by different automation/routine apps to do the functionality you want. or even better, for automation apps specifically that can receive broadcasts, the app sends a broadcast when a mode is selected alongside the silent notification (there's a guide in the app for detailed info) 
- **Both panels are triggered using side buttons** with up to 7 different combinations (custom buttons included for devices that has an additional button)

- **New volume system** that reads your phone volume steps, multiplies it by 10 and here you go with 150, 250, or 300 steps! (pretty easy right? i got cooked implementing this)
- **Individual app volume is here!** options to choose from are showing it alongside volume slider or only in audio mixer (its optional mode) **Must note that it uses Shizuku**
- **Volume Counter:** Show volume count on your volume slider, applies to individual app volume slider too.
- **Shizuku Implementation:** Grant all permissions with a single click, manage cloaked apps that the app hides its accessibility from (eg. banking apps), watchdog to keep the app alive, watchdog to keep permissions always active (both watchdogs are mainly for hardened systems, but anyone can use them)
- **Added all panels activity:** all panels now are accessible from activity launcher or any similar feature in different apps. 
- **Audio Mixer:** Option to change its position on X & Y axis and a scale to make it bigger/smaller. there's also an option to attach it to the volume slider or leave it floating. 
- **Want haptics too? there's an option for that**

 ---

## Play protect flagged the app?

- **Method 1: Pause Play Protect:** Open Google Play Store, tap your Profile Icon (top right), Choose Play Protect, tab the Settings icon (top right) and Toggle OFF "Scan apps with Play Protect" and tap Pause. Install Nudge apk.
- **Method 2: Install Using Obtainium:** Click the badge on top to add the repo and install the app
- **Method 3: ADB/Shizuku:** run "adb install nudge.apk" or using 3rd-party installers like InstallerX Revived.

## Accessibility denied?

- **Method 1: Manually:** Users on Android 13+ after installing, go to Settings, Apps, Nudge, 3 dots menu on top right corner (⋮), Allow restricted settings so the accessibility toggle isn't grayed out for the app. now go back to accessibility page and enable it.
- **Method 2: Automatically via Shizuku in-app**
  
 ## Two variants of the app
- Online Variant has internet permission and shows app updates and checks for them in selected periods or manually and provides in-app updates, while Offline version doesnt show updates at all and doesnt have internet permission
