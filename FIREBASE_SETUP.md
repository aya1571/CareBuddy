# CareBuddy Firebase setup

The Android app uses Firebase Authentication and Cloud Firestore. Verification images are compressed and stored as Base64 strings in Firestore, so this project does not require Firebase Storage or a paid Storage plan.

## One-time Firebase Console setup

1. In **Authentication → Sign-in method**, enable **Email/Password**.
2. In **Firestore Database**, create a database in **production mode**. Open its **Rules** tab and publish the contents of this project's `firestore.rules` file.
3. No Storage bucket or billing plan is needed. The app limits compressed identity photos to fit Firestore document size limits. Publish the updated `firestore.rules` in this folder; the verification screen must use Firestore to save and review these Base64 images. Identity images are sensitive personal data: only use test identities for a school project, restrict project access, and delete test records when finished.
4. The Android Firebase app must use package name `com.example.carebuddy` and its matching `google-services.json` must remain at `app/google-services.json`.
5. To use **Continue with Google**, enable Google in **Authentication → Sign-in method**, add this app's SHA-1 fingerprint in the Firebase Android app settings, then download the refreshed `google-services.json` and replace the existing file in `app/`. Google sign-in is free and does not require Firebase Storage or a billing upgrade. Keep email/password sign-in available for people without a Google account. The Google button supports elderly and companion accounts; Admin sign-in remains restricted to the provisioned admin account.

## Create the first admin account

Admin accounts cannot be self-registered in the app. Create an email/password user in **Authentication → Users**, copy that user's UID, then create a Firestore document in collection `users` whose document ID is that UID. Add these fields:

| Field | Type | Value |
| --- | --- | --- |
| `uid` | string | the same UID |
| `email` | string | the admin email |
| `displayName` | string | the admin's name |
| `role` | string | `admin` |
| `status` | string | `Active` |
| `verificationStatus` | string | `approved` |

The app's Admin role sign-in then checks that Firestore account. Create the admin document in the Firebase Console, which has project-owner access; don't add a public admin registration option.

## First end-to-end run

Register an Elderly account and a Companion account in the app. Existing Google-enabled CareBuddy accounts can use **Continue with Google**. The first Google sign-in for a new CareBuddy account asks for a role (selected on the login screen) and a phone number, creates its Firestore profile, and continues to IC verification. After consent, the app saves compressed IC and face images directly to Firestore for admin review. Accounts without images remain pending; an admin may only approve such a record after checking the original documents in person. Sign in as the Companion and set availability from the actual profile. The Elderly companion list shows approved profiles. Booking requests and updates are shared through Firestore while both devices are online.

## Services still needing a provider

Payment method selection and estimates are in the app, but the app does not charge money or transfer payouts. Notifications currently appear in the app; remote push notifications need Firebase Cloud Messaging plus a trusted server-side sender. Those integrations require the payment provider and deployment configuration you choose.
