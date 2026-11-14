# ZEDX AI Admin Website (`BY THE TITAN AI`)

Standalone responsive web admin console for **ZEDX AI** connected to Firebase project `gen-lang-client-0800058431` and Firestore database `ai-studio-android-zedxai-8ef47d02-6b3b-4e87-be23-acd8a14f183a`.

## Security Guarantees
- Admin access is gated strictly by **backend-controlled Firebase Auth custom claims** (`admin: true`) verified both in the Admin UI and in `firestore.rules`.
- Normal users cannot promote themselves to Admin.
- Private user conversations (`/users/{uid}/conversations`), private memories (`/users/{uid}/memories`), and on-device AES-256-GCM API keys are **never exposed** to the Admin Website.

## Setup Instructions

1. **Register a Web App in Firebase Console**
   - Open Firebase Console for project `gen-lang-client-0800058431`.
   - Click **Add app -> Web**, then copy `apiKey` and `appId` into `admin-web/firebase-config.js`.
   - Enable **Google** as a Sign-In provider in Firebase Authentication and authorize your hosting domain (`localhost` for local testing).

2. **Grant Admin Custom Claims to Owner (`pranavji628@gmail.com`)**
   Run this once from a trusted server/admin environment using `firebase-admin`:

   ```js
   const admin = require("firebase-admin");
   admin.initializeApp({ projectId: "gen-lang-client-0800058431" });

   async function grantOwnerAdmin() {
     const user = await admin.auth().getUserByEmail("pranavji628@gmail.com");
     await admin.auth().setCustomUserClaims(user.uid, { admin: true, role: "admin" });
     console.log("Granted admin claim to:", user.uid);
   }
   grantOwnerAdmin();
   ```

3. **Serve Locally or Deploy to Firebase Hosting**
   ```bash
   npx serve admin-web
   ```
   Or deploy with Firebase Hosting pointing `public` to `admin-web`.
