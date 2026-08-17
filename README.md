# Secure License Guardian

EXISTING ANDROID APPLICATION — SECURITY + LICENSE + REMOTE ENDPOINT UPGRADE

You are working on an EXISTING Android application project provided as a ZIP.

CRITICAL RULE

DO NOT rebuild the application from scratch.

DO NOT remove existing functionality.

DO NOT redesign existing screens unnecessarily.

DO NOT replace working modules with dummy implementations.

First deeply analyze the complete existing source code, Android structure, server/API, database, admin panel, license system, authentication, plans system, and all existing flows.

Then modify the existing project in-place while preserving all current functionality.

The goal is to add a professional, secure, server-controlled licensing system, Firebase-based remote API endpoint configuration, device/account restriction, and security hardening.

1. EXISTING SYSTEM MUST REMAIN FUNCTIONAL

Everything that currently works must continue working:

Existing login

Existing registration

Existing application features

Existing dashboard

Existing plans

Existing server/API

Existing database

Existing admin panel

Existing license infrastructure

Existing user data

Existing application screens

Existing business logic

Do not remove any existing feature unless it directly conflicts with the new security/license architecture.

Reuse existing classes and APIs wherever possible.

Before modifying anything, inspect:

Android source

Java/Kotlin classes

Activities

Fragments

Services

API clients

License classes

Authentication classes

Plan classes

Server PHP/API files

MySQL/database structure

Admin panel

Existing device management

Existing license management

2. NEW ARCHITECTURE

The final architecture must be:

ANDROID APP ↓ Firebase Remote Configuration ↓ Current API Endpoint ↓ Server/API ↓ MySQL Database ↓ Super Admin Panel

IMPORTANT:

Firebase is ONLY for remotely changing the API/server endpoint.

Firebase must NOT become the license authority.

Firebase must NOT store licenses.

Firebase must NOT store users.

Firebase must NOT store passwords.

Firebase must NOT store license activation data.

Firebase must NOT store sensitive business data.

3. FIREBASE REMOTE ENDPOINT SYSTEM

Create a Firebase-based remote endpoint configuration.

The application should retrieve:

API_BASE_URL

from Firebase.

Example:

https://server1.example.com/api/

If later the server moves to:

https://server2.example.com/api/

I should only need to change the Firebase value.

I should NOT need to rebuild the APK.

The application should automatically use the new endpoint after refreshing configuration.

Local fallback

The application must cache the last successfully fetched endpoint locally.

If Firebase is temporarily unavailable:

Use the last known valid endpoint.

Do NOT break the entire application simply because Firebase is temporarily unavailable.

Endpoint validation

Before accepting a Firebase endpoint:

HTTPS only

Validate URL format

Reject obviously invalid URLs

Never execute arbitrary Firebase-provided code

Never allow Firebase configuration to inject code

Only allow endpoint configuration

4. PERMANENT API KEY

There will be one application API key.

I will manually provide this API key during development.

The API key must NOT be stored in:

Firebase

MySQL

Admin panel

License database

User database

The API key should be compiled into the application and protected using appropriate Android release security techniques.

IMPORTANT:

Never leave the API key as an obvious plain-text constant such as:

API_KEY = "123456"

Use appropriate obfuscation/protection and R8/ProGuard.

However, understand that any secret shipped inside an APK cannot be considered absolutely secret.

Therefore the server MUST NOT rely solely on this API key for authorization.

The API key is an additional application authentication layer, not the sole security mechanism.

5. SERVER-SIDE LICENSE AUTHORITY

The server/database remains the ONLY authority for license validity.

The Android application must never be able to locally declare:

license = true

or:

licenseActive = true

and bypass the server.

The server must verify:

User

Account

Device

License

License status

License expiry

License activation

License duration

Account status

The client only displays the result returned by the server.

6. NEW USER REGISTRATION

When a completely new user opens the application:

Show existing/new account registration flow.

When registration succeeds:

Create the user account on the server.

At the same time register the device binding.

RULE:

ONE DEVICE = ONE NEW ACCOUNT

If the same device attempts to create another new account:

Reject registration.

Display an appropriate message such as:

"This device is already registered with an account."

Do not rely only on a locally stored device ID.

The actual restriction MUST be enforced server-side.

Use a privacy-conscious, stable device/app installation identifier rather than collecting unnecessary hardware identifiers.

7. NEW ACCOUNT LICENSE STATE

Immediately after successful registration:

The account exists but its application functionality remains locked until a license is activated.

The server should return:

account status

license status

license expiry

device binding status

request status

Possible license states:

PENDING ACTIVE EXPIRED REVOKED BLOCKED

8. LICENSE REQUEST

Immediately after a new account is created:

Create a license request visible in Super Admin.

Super Admin should be able to see:

Username

Account ID

Device/installation identifier

Registration date

Current status

Requested plan

License status

License expiry

Last activity

Device information that is safe and appropriate to display

Do not expose unnecessary sensitive information.

9. APPLICATION LOCK SCREEN / LICENSE BANNER

While the license is not active:

The application's existing functionality must remain locked.

Every relevant application screen should show a persistent top area/banner:

"VERIFY YOUR LICENSE"

and:

"Contact for License Activation"

The Contact button should open the configured WhatsApp/support contact.

The support number must be controlled by Super Admin.

Do not duplicate the number throughout the Android source.

Fetch the configured support number from the server/admin configuration.

10. WHATSAPP SUPPORT NUMBER

Super Admin must be able to change the WhatsApp/support number.

Example:

+92XXXXXXXXXX

After changing it from Super Admin:

The application should receive the new number from the server.

Do not require a new APK.

The WhatsApp button should open the appropriate WhatsApp contact/action.

11. SUPER ADMIN LICENSE SYSTEM

Upgrade the existing Super Admin panel.

Super Admin must have:

License Requests

Pending requests

Active licenses

Expired licenses

Revoked licenses

Blocked users

License Actions

Activate

Extend

Renew

Revoke

Suspend

Reactivate

License Duration

Allow predefined plans.

Also allow:

CUSTOM DAYS

Example:

Custom Days: 47

Then activate license for exactly 47 days.

The server must calculate expiry using server-side time.

Never trust the Android device's clock for license expiry.

12. LICENSE PLANS

Existing Plans system should remain.

Make all relevant plans visible/manageable inside Super Admin.

Super Admin should be able to:

View plans

Add plans

Edit plans

Enable/disable plans

Set duration

Set price

Set credits where applicable

Set description

Set status

Do not unnecessarily modify the application's existing plan behavior.

13. CREDITS SYSTEM

Add/maintain a Super Admin credit management system.

Super Admin should be able to manually modify a user's credits.

Actions:

Add credits

Remove credits

Set credits

View current credits

Every manual credit change should create an audit record:

Admin

User

Old value

New value

Difference

Timestamp

Reason if available

Do not allow users to modify their own credits through client-side manipulation.

14. LICENSE VERIFICATION

When a valid license is entered:

The application must send the verification request to the server.

Server verifies:

License exists

License belongs to account

Device matches

License is not revoked

License is not expired

Account is not blocked

License activation is valid

If valid:

Return a secure server response.

Immediately show a professional popup:

CONGRATULATIONS!

Your License Has Been Verified Successfully.

License Duration: [actual duration]

License Expiry: [actual expiry]

If license is:

365 days

show:

Your License Has Been Verified for 1 Year.

If:

30 days

show:

Your License Has Been Verified for 30 Days.

If:

47 days

show:

Your License Has Been Verified for 47 Days.

Do NOT hard-code "1 Year".

Use actual server-provided license information.

15. USERNAME + PASSWORD CONFIRMATION

After successful license verification:

Show the required confirmation screen.

The user must confirm their:

Username Password

before the application becomes fully unlocked.

IMPORTANT SECURITY RULE:

Never store the user's plaintext password.

If the existing authentication system uses secure password hashing/server authentication, preserve it.

The confirmation must happen through the existing secure authentication mechanism.

After successful confirmation:

Unlock the application's existing features.

16. LICENSE EXPIRY

When the license expires:

The server must return:

LICENSE_EXPIRED

The application automatically locks protected functionality.

Show:

"Your License Has Expired"

with:

"Verify Your License"

and:

"Contact for License Activation"

buttons.

If Super Admin renews the license:

The application should detect the new status on the next verification/sync.

No APK update required.

17. SERVER TIME

License expiration must NEVER depend on:

System.currentTimeMillis() alone Device date Device timezone User changing phone date/time

Use server-side timestamps.

The server/database is authoritative.

18. SECURITY HARDENING — ANDROID

Apply release security hardening.

Use:

R8

ProGuard

Code shrinking

Resource shrinking

Obfuscation

Debug disabled in release

Secure HTTPS networking

Certificate/public-key pinning where compatible with the deployment architecture

Tamper detection

Basic root/debug detection where appropriate

APK signature validation where practical

Secure local storage for non-sensitive configuration

No plaintext passwords

No sensitive logs in release builds

Do not rely on root detection or obfuscation as the primary license security mechanism.

The real security boundary is the server.

19. PREVENT SIMPLE LICENSE BYPASS

Search the entire Android project for:

licenseActive isLicensed licenseValid premium subscription planActive unlock isPremium expiry

Make sure there is no simple client-side Boolean that an attacker can modify to unlock everything.

Protected API operations should also require server-side authorization.

If an attacker modifies the UI, the server must still reject unauthorized protected requests.

20. API SECURITY

Every sensitive API endpoint must validate authentication and authorization.

Do not trust:

user_id sent by client

license_id sent by client

device_id sent by client

plan_id sent by client

credit amount sent by client

Validate ownership and permissions server-side.

Use prepared SQL statements everywhere.

Validate all incoming parameters.

Use appropriate authentication tokens/session handling.

Prevent:

SQL Injection

IDOR

unauthorized license activation

unauthorized credit changes

unauthorized user modification

replay where relevant

brute-force attacks

excessive API requests

Add reasonable API rate limiting.

21. ADMIN SECURITY

Super Admin must be the only authority capable of:

License activation

License extension

License revocation

Manual days

Plan modification

Credit modification

Support number modification

User blocking/unblocking

Normal users must never access these endpoints.

Enforce admin authorization server-side.

Never trust hidden admin buttons as security.

22. AUDIT LOGS

Create/use an audit log system.

Record important Super Admin actions:

License activation

License extension

License revocation

User blocking

User unblocking

Credit modification

Plan modification

Support number change

Endpoint/configuration change if applicable

Store:

Admin ID

Action

Target

Old value

New value

Timestamp

IP where appropriate

23. FIREBASE FAILURE HANDLING

Firebase is NOT the license authority.

If Firebase fails:

Use cached last-valid endpoint.

Continue normal API communication if that endpoint is reachable.

Do not silently switch to an untrusted endpoint.

Do not disable license verification.

Do not unlock premium functionality.

24. DO NOT STORE THESE IN FIREBASE

Never store:

Passwords

License keys

License status

User accounts

Credits

Payment information

Database credentials

Admin credentials

Sensitive personal data

Firebase only contains the minimum remote endpoint/configuration values required.

25. DATABASE CHANGES

Before modifying database structure:

Inspect the existing schema.

Do not destroy existing tables/data.

Use migrations or ALTER statements where appropriate.

Add only required fields/tables.

Potential fields/tables may include:

users devices license_requests licenses plans credit_transactions admin_audit_logs system_settings

But DO NOT blindly create duplicates if equivalent structures already exist.

Reuse existing tables whenever possible.

26. BACKWARD COMPATIBILITY

Existing users must not unnecessarily lose access.

Create a safe migration strategy for existing accounts/licenses.

Before changing existing license logic:

Analyze how existing licenses are stored.

Preserve valid existing licenses.

Do not automatically expire existing active licenses because of the upgrade.

27. PERFORMANCE

The license/configuration system must not make the application slow.

Do not call the license API repeatedly on every UI redraw.

Use sensible caching.

Suggested behavior:

Fetch endpoint/config when required

Cache endpoint

Check license status at application startup/resume according to a sensible interval

Immediately verify after license activation

Refresh after admin-side renewal is detected

Do not create unnecessary API traffic.

28. UI

Keep the existing application UI.

Only add the required license-related UI:

Top license status/banner.

Buttons:

VERIFY YOUR LICENSE CONTACT FOR LICENSE ACTIVATION

Use the application's existing design language.

Do not redesign the entire application.

The license popup should look professional and native to the existing application.

29. DEVELOPMENT CONFIGURATION

Create one clearly documented place where I can manually provide:

API_KEY

The API key should then be protected through the release build process.

Create a clear configuration mechanism such as:

BuildConfig/API configuration/constants

but do NOT expose it through the UI.

Do not put the API key into Firebase.

30. RELEASE SECURITY

Ensure:

Debug builds can be used for development.

Release builds have:

R8 enabled

Obfuscation enabled

Debugging disabled

Logging minimized

Sensitive logs removed

Proper signing configuration documented

Network security hardened

Do not break the development build while implementing release security.

31. TEST EVERYTHING

After implementation test:

Registration

New account

Duplicate account

Same device second account

Server rejection

Network failure

License

Pending

Active

Expired

Revoked

Blocked

Manual 7 days

Manual 30 days

Manual 365 days

Custom 47 days

Firebase

Endpoint changes

New endpoint

Invalid endpoint

Firebase unavailable

Cached endpoint fallback

Admin

Activate

Extend

Revoke

Block

Unblock

Credits

Plans

WhatsApp number

Security

Attempt to verify that:

Client cannot activate its own license

Client cannot extend license

Client cannot modify credits

Client cannot bypass device restriction

Expired license cannot access protected server operations

User cannot access admin endpoints

Changing phone date does not extend license

Simple APK Boolean modification does not grant server authorization

32. IMPORTANT — DO NOT BREAK EXISTING PROJECT

Do NOT:

Rewrite everything

Delete existing modules

Replace working APIs unnecessarily

Remove existing features

Create fake/demo data

Create mock license verification

Create frontend-only licensing

Move licensing into Firebase

Store passwords in Firebase

Store licenses in Firebase

Hard-code support number throughout the application

Hard-code the API endpoint permanently

Use the existing project architecture wherever possible.

33. FINAL DELIVERABLE

After implementation provide:

Complete modified Android project.

Complete modified server/API.

Complete modified Super Admin panel.

Database migration/SQL.

Firebase configuration instructions.

Exact location where API key must be entered.

Exact Firebase parameter name for API endpoint.

Release build instructions.

Security configuration instructions.

List of modified files.

List of newly created files.

Explanation of how existing users/licenses are migrated.

Testing checklist.

Any security limitations that cannot technically be eliminated on an APK/client device.

IMPORTANT:

Do not claim that the APK is "100% unhackable".

The objective is to make the system resistant to reverse engineering, APK modification, license bypass, API abuse, and unauthorized admin operations while keeping the server as the ultimate authority.

FIRST analyze the complete provided project.

THEN implement the changes.

DO NOT start by deleting or replacing the existing project.

Preserve all existing application functionality unless a change is required for the security architecture above.

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/7e04a0d0-8d54-4381-b5e8-69fb57301aa0).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
