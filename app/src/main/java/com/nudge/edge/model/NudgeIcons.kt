package com.nudge.edge.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

data class IconEntry(
    val key: String,
    val name: String,
    val icon: ImageVector
)

data class IconCategory(
    val name: String,
    val icons: List<IconEntry>
)

object NudgeIcons {

    val SlashedBell: ImageVector by lazy {
        ImageVector.Builder(
            name = "SlashedBell",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12.0f, 4.2f)
                curveTo(11.4f, 4.2f, 11.0f, 4.6f, 11.0f, 5.1f)
                lineTo(11.0f, 5.3f)
                curveTo(12.6f, 5.6f, 14.1f, 6.4f, 15.1f, 7.7f)
                curveTo(16.3f, 9.1f, 16.9f, 11.0f, 16.9f, 13.0f)
                lineTo(16.9f, 14.2f)
                lineTo(18.9f, 16.2f)
                curveTo(19.0f, 15.8f, 19.0f, 15.4f, 19.0f, 15.0f)
                lineTo(18.0f, 14.0f)
                lineTo(18.0f, 13.0f)
                curveTo(18.0f, 10.3f, 16.2f, 7.9f, 13.6f, 7.2f)
                lineTo(13.6f, 5.1f)
                curveTo(13.6f, 4.6f, 13.2f, 4.2f, 12.0f, 4.2f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(5.3f, 17.5f)
                lineTo(16.8f, 17.5f)
                lineTo(14.8f, 15.5f)
                lineTo(7.1f, 15.5f)
                curveTo(7.1f, 14.4f, 7.3f, 13.3f, 7.8f, 12.3f)
                lineTo(6.4f, 10.9f)
                curveTo(5.5f, 12.5f, 5.0f, 14.4f, 5.0f, 16.5f)
                curveTo(5.0f, 17.1f, 5.1f, 17.3f, 5.3f, 17.5f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(10.5f, 18.5f)
                curveTo(10.5f, 19.6f, 11.4f, 20.5f, 12.5f, 20.5f)
                curveTo(13.2f, 20.5f, 13.8f, 20.1f, 14.2f, 19.5f)
                lineTo(11.5f, 16.8f)
                curveTo(10.9f, 17.2f, 10.5f, 17.8f, 10.5f, 18.5f)
                close()
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4.2f, 4.2f)
                lineTo(19.8f, 19.8f)
            }
        }.build()
    }

    val BeamedMusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "BeamedMusicNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(8.4f, 5.8f)
                lineTo(19.0f, 3.8f)
                curveTo(19.2f, 3.8f, 19.4f, 4.1f, 19.4f, 4.5f)
                lineTo(19.4f, 14.2f)
                curveTo(19.4f, 16.5f, 17.8f, 18.2f, 16.0f, 18.5f)
                curveTo(14.2f, 18.8f, 12.8f, 17.5f, 12.8f, 15.8f)
                curveTo(12.8f, 14.1f, 14.3f, 12.8f, 16.2f, 12.8f)
                curveTo(16.8f, 12.8f, 17.3f, 13.0f, 17.7f, 13.3f)
                lineTo(17.7f, 7.0f)
                lineTo(10.1f, 8.5f)
                lineTo(10.1f, 17.2f)
                curveTo(10.1f, 19.5f, 8.5f, 21.2f, 6.7f, 21.5f)
                curveTo(4.9f, 21.8f, 3.5f, 20.5f, 3.5f, 18.8f)
                curveTo(3.5f, 17.1f, 5.0f, 15.8f, 6.9f, 15.8f)
                curveTo(7.5f, 15.8f, 8.0f, 16.0f, 8.4f, 16.3f)
                lineTo(8.4f, 5.8f)
                close()
            }
        }.build()
    }

    val ACCENT_COLORS = listOf(
        0xFFFF453AL,
        0xFFFF9F0AL,
        0xFFFFD60AL,
        0xFF30D158L,
        0xFF63E6E2L,
        0xFF64D2FFL,
        0xFF0A84FFL,
        0xFF5E5CE6L,
        0xFFBF5AF2L,
        0xFFFF375FL,
        0xFFFFFFFFL,
        0xFF8E8E93L
    )

    val CATEGORIES = listOf(
        IconCategory(
            name = "Audio & Alerts",
            icons = listOf(
                IconEntry("Notifications", "Bell", Icons.Rounded.Notifications),
                IconEntry("NotificationsOff", "Slashed Bell", SlashedBell),
                IconEntry("Vibration", "Vibration", Icons.Rounded.Vibration),
                IconEntry("VolumeUp", "Volume Up", Icons.AutoMirrored.Rounded.VolumeUp),
                IconEntry("VolumeOff", "Volume Off", Icons.AutoMirrored.Rounded.VolumeOff),
                IconEntry("MusicNote", "Music Note", BeamedMusicNote),
                IconEntry("Headphones", "Headphones", Icons.Rounded.Headphones),
                IconEntry("MicOff", "Mic Off", Icons.Rounded.MicOff)
            )
        ),
        IconCategory(
            name = "Daily & Lifestyle",
            icons = listOf(
                IconEntry("Bedtime", "Moon / Bedtime", Icons.Rounded.Bedtime),
                IconEntry("WbSunny", "Sun", Icons.Rounded.WbSunny),
                IconEntry("Work", "Work / Briefcase", Icons.Rounded.Work),
                IconEntry("Person", "Person", Icons.Rounded.Person),
                IconEntry("Home", "Home", Icons.Rounded.Home),
                IconEntry("School", "School / Study", Icons.Rounded.School),
                IconEntry("MenuBook", "Reading Book", Icons.AutoMirrored.Rounded.MenuBook),
                IconEntry("Coffee", "Coffee", Icons.Rounded.Coffee)
            )
        ),
        IconCategory(
            name = "Activities & Gaming",
            icons = listOf(
                IconEntry("SportsEsports", "Gaming", Icons.Rounded.SportsEsports),
                IconEntry("FitnessCenter", "Gym / Dumbbell", Icons.Rounded.FitnessCenter),
                IconEntry("DirectionsRun", "Running", Icons.AutoMirrored.Rounded.DirectionsRun),
                IconEntry("Movie", "Cinema / Movie", Icons.Rounded.Movie),
                IconEntry("Code", "Coding / Dev", Icons.Rounded.Code),
                IconEntry("Brush", "Creative / Brush", Icons.Rounded.Brush)
            )
        ),
        IconCategory(
            name = "Travel & Outdoors",
            icons = listOf(
                IconEntry("DirectionsCar", "Driving / Car", Icons.Rounded.DirectionsCar),
                IconEntry("Flight", "Airplane / Flight", Icons.Rounded.Flight),
                IconEntry("DirectionsBike", "Cycling / Bike", Icons.AutoMirrored.Rounded.DirectionsBike),
                IconEntry("Train", "Train / Transit", Icons.Rounded.Train),
                IconEntry("Explore", "Compass / Explore", Icons.Rounded.Explore),
                IconEntry("Landscape", "Landscape", Icons.Rounded.Landscape)
            )
        ),
        IconCategory(
            name = "System & Focus",
            icons = listOf(
                IconEntry("SelfImprovement", "Meditation / Focus", Icons.Rounded.SelfImprovement),
                IconEntry("DoNotDisturbOn", "Do Not Disturb", Icons.Rounded.DoNotDisturbOn),
                IconEntry("Shield", "Shield / Security", Icons.Rounded.Shield),
                IconEntry("Bolt", "Performance / Bolt", Icons.Rounded.Bolt),
                IconEntry("BatterySaver", "Battery Saver", Icons.Rounded.BatterySaver),
                IconEntry("WifiOff", "Offline / Wifi Off", Icons.Rounded.WifiOff),
                IconEntry("Bluetooth", "Bluetooth", Icons.Rounded.Bluetooth),
                IconEntry("CameraAlt", "Camera", Icons.Rounded.CameraAlt),
                IconEntry("Favorite", "Heart / Favorite", Icons.Rounded.Favorite),
                IconEntry("Star", "Star", Icons.Rounded.Star),
                IconEntry("Lock", "Lock", Icons.Rounded.Lock),
                IconEntry("Schedule", "Schedule / Clock", Icons.Rounded.Schedule)
            )
        ),
        IconCategory(
            name = "Uncategorized",
            icons = listOf(
                IconEntry("FlashlightOn", "Flashlight", Icons.Rounded.FlashlightOn),
                IconEntry("Wifi", "Wi-Fi", Icons.Rounded.Wifi),
                IconEntry("WifiTethering", "Hotspot", Icons.Rounded.WifiTethering),
                IconEntry("Nfc", "NFC", Icons.Rounded.Nfc),
                IconEntry("LocationOn", "Location", Icons.Rounded.LocationOn),
                IconEntry("Cast", "Cast", Icons.Rounded.Cast),
                IconEntry("DarkMode", "Dark Mode", Icons.Rounded.DarkMode),
                IconEntry("LightMode", "Light Mode", Icons.Rounded.LightMode),
                IconEntry("ScreenRotation", "Auto Rotate", Icons.Rounded.ScreenRotation),
                IconEntry("Calculate", "Calculator", Icons.Rounded.Calculate),
                IconEntry("Timer", "Timer", Icons.Rounded.Timer),
                IconEntry("AvTimer", "Stopwatch", Icons.Rounded.AvTimer),
                IconEntry("AccessAlarm", "Alarm Clock", Icons.Rounded.AccessAlarm),
                IconEntry("CalendarToday", "Calendar", Icons.Rounded.CalendarToday),
                IconEntry("AccountBalanceWallet", "Wallet", Icons.Rounded.AccountBalanceWallet),
                IconEntry("ShoppingCart", "Shopping Cart", Icons.Rounded.ShoppingCart),
                IconEntry("Restaurant", "Restaurant", Icons.Rounded.Restaurant),
                IconEntry("LocalCafe", "Coffee Cup", Icons.Rounded.LocalCafe),
                IconEntry("LocalBar", "Bar / Drink", Icons.Rounded.LocalBar),
                IconEntry("Hotel", "Hotel / Bed", Icons.Rounded.Hotel),
                IconEntry("Pets", "Pets", Icons.Rounded.Pets),
                IconEntry("ChildCare", "Baby / Child", Icons.Rounded.ChildCare),
                IconEntry("MedicalServices", "Health / First Aid", Icons.Rounded.MedicalServices),
                IconEntry("Build", "Wrench / Tool", Icons.Rounded.Build),
                IconEntry("Handyman", "Handyman / Tools", Icons.Rounded.Handyman),
                IconEntry("BugReport", "Bug / Debug", Icons.Rounded.BugReport),
                IconEntry("Terminal", "Terminal", Icons.Rounded.Terminal),
                IconEntry("Memory", "Chip / Memory", Icons.Rounded.Memory),
                IconEntry("Router", "Router", Icons.Rounded.Router),
                IconEntry("Tv", "TV", Icons.Rounded.Tv),
                IconEntry("Watch", "Smartwatch", Icons.Rounded.Watch),
                IconEntry("Speaker", "Speaker", Icons.Rounded.Speaker),
                IconEntry("Earbuds", "Earbuds", Icons.Rounded.Earbuds),
                IconEntry("Mic", "Microphone", Icons.Rounded.Mic),
                IconEntry("Videocam", "Video Camera", Icons.Rounded.Videocam),
                IconEntry("PhotoLibrary", "Gallery", Icons.Rounded.PhotoLibrary),
                IconEntry("Palette", "Palette", Icons.Rounded.Palette),
                IconEntry("Extension", "Plugin / Puzzle", Icons.Rounded.Extension),
                IconEntry("RocketLaunch", "Rocket / Boost", Icons.Rounded.RocketLaunch),
                IconEntry("ThumbUp", "Thumbs Up", Icons.Rounded.ThumbUp),
                IconEntry("Visibility", "Eye / Visible", Icons.Rounded.Visibility),
                IconEntry("VisibilityOff", "Hidden / Invisible", Icons.Rounded.VisibilityOff),
                IconEntry("VpnKey", "Key / Security", Icons.Rounded.VpnKey),
                IconEntry("Cloud", "Cloud", Icons.Rounded.Cloud),
                IconEntry("Download", "Download", Icons.Rounded.Download),
                IconEntry("Folder", "Folder", Icons.Rounded.Folder),
                IconEntry("Delete", "Trash / Delete", Icons.Rounded.Delete),
                IconEntry("Share", "Share", Icons.Rounded.Share),
                IconEntry("Public", "Globe / Public", Icons.Rounded.Public),
                IconEntry("Translate", "Translate / Languages", Icons.Rounded.Translate),
                IconEntry("QrCodeScanner", "QR Code", Icons.Rounded.QrCodeScanner),
                IconEntry("Fingerprint", "Fingerprint", Icons.Rounded.Fingerprint),
                IconEntry("VerifiedUser", "Verified", Icons.Rounded.VerifiedUser),
                IconEntry("AutoAwesome", "Sparkles / AI", Icons.Rounded.AutoAwesome),
                IconEntry("Tune", "Sliders / Tune", Icons.Rounded.Tune),
                IconEntry("Speed", "Speedometer", Icons.Rounded.Speed)
            )
        )
    )

    private val iconMap: Map<String, ImageVector> by lazy {
        CATEGORIES.flatMap { it.icons }.associate { it.key to it.icon }
    }

    fun getIcon(key: String): ImageVector {
        return iconMap[key] ?: Icons.Rounded.Notifications
    }
}
