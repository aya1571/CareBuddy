# CareBuddy User Guide

CareBuddy is a student project for arranging companionship visits. Sign in with the correct role: **Elderly**, **Companion**, or **Admin**. Both devices need internet access for changes to sync through Firebase.

## Elderly user

1. Register or sign in. Complete IC verification and wait for an administrator to review the submission.
2. Open **Find Companion**, search or filter, and choose an approved companion.
3. Choose an activity, date, time, and location. Review the estimate and submit the booking request.
4. Find requests in **My Bookings → Upcoming**. Pending and accepted requests are shown there. Open a booking for details, chat, or to call the companion.
5. When a companion is travelling, open the booking details. If **Track Companion** is available, use it to view the companion's latest shared position. Tracking requires the companion to start the trip, grant location permission, and have internet and location services enabled. Sharing stops when the companion marks arrival.
6. During an active booking, **Emergency Help** offers 999 or your saved emergency contact. CareBuddy opens the phone dialer; confirm the call on your phone. Only call 999 for a genuine emergency.
7. After the booking is completed, open it to leave a review if the review option is available.

## Companion

1. Register as a companion and submit the requested verification documents. An administrator must approve the account before it appears as bookable.
2. Set availability and profile details from the companion screens.
3. Open **Booking Requests** to review requests. Accept or decline; accepted and active bookings remain available from the active booking list.
4. Open an accepted booking to chat or contact the customer. If trip tracking is available, tap **Start Trip and Share Location** and allow location access. A foreground notification indicates that sharing is active. Tap **Arrived • Start Service** when you reach the customer, then mark the visit complete when finished.
5. Earnings and completed work are project records; no payout is sent by the app.

## Admin

Sign in with the provisioned Admin account. The Admin area contains:

1. **Admin Login** — sign in to the administrative account.
2. **Dashboard** — see the system overview and shortcuts to management screens.
3. **User Management** — view and manage elderly user accounts.
4. **Companion Management** — view and manage companion accounts and their status.
5. **Verification** — review submitted identity or companion documents and approve or reject them after checking the information.
6. **Booking Management** — review bookings and their current statuses.
7. **Payment Management** — review payment records and their recorded statuses. The app does not process real payments.
8. **Reports/Reviews** — review booking summaries and submitted user reviews.
9. **Settings** — access administrative settings and support information.

## Payments and notifications

Payment choices and totals are stored as booking information only. CareBuddy does **not** charge a card, process online banking or Touch 'n Go, or transfer payouts. In-app notifications are available; remote push delivery requires additional Firebase Cloud Messaging and server setup.

## Firebase setup notes for the project owner

See [FIREBASE_SETUP.md](FIREBASE_SETUP.md) for enabling sign-in, publishing rules, and creating the first admin account. Never place private service-account credentials in the Android app.
