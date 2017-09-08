package models.settings

import enumeratum._

sealed abstract class SettingKey(val title: String, val description: String, val default: String) extends EnumEntry

object SettingKey extends Enum[SettingKey] with CirceEnum[SettingKey] {
  case object TestSetting extends SettingKey(
    title = "Test Setting",
    description = "A simple test.",
    default = "Hello!"
  )

  override val values = findValues
}
