namespace QuickTap.App;

/// <summary>
/// Backend coordinates. These match the credentials the Android build sends so
/// both clients talk to the same Super Admin panel. Override per environment at
/// build time if needed.
/// </summary>
public static class AppConfig
{
    public const string ApiBaseUrl = "https://mediumaquamarine-baboon-263984.hostingersite.com/api/";
    public const string AppId = "quicktap-pos";
    public const string ApiKey = "REPLACE_WITH_BUILD_API_KEY";
    public const string ApiSecret = "REPLACE_WITH_BUILD_API_SECRET";

    public const string ProductName = "QuickTap POS";
    public const string Credit = "Developed by your mirza";
}
