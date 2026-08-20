# Privacy Policy for VividOrbit

**Last Updated:** August 20, 2026  
**Developer:** Voryn Labs  
**Contact Email:** appsvorynlabs@gmail.com  
**Application:** VividOrbit (Android TV)  

---

### 1. Overview
VividOrbit is an open-source, privacy-first Live TV channel management and guide application designed for Android TV and Google TV devices. We believe your entertainment habits, channel choices, and TV viewing data belong strictly to you.

---

### 2. Information We Collect
**VividOrbit does NOT collect, store, transmit, or monetize any personal data.**

- **No Personal Identifiable Information (PII):** We do not collect your name, email, phone number, location, or payment details.
- **No Analytics / Telemetry / Tracking:** We do not embed third-party analytics SDKs (e.g., Firebase Analytics, Google Analytics, Mixpanel) or advertising networks.
- **No Cloud Sync or Remote Storage:** All channel numbers, favorite lists, custom orderings, and remote key mappings are saved exclusively in local storage on your Android TV device.

---

### 3. Permissions Used & Purpose
VividOrbit requests only the minimal permissions required to function as an Android TV Live Channel manager:

| Permission | Purpose |
| :--- | :--- |
| `READ_TV_LISTINGS` & `READ_EPG_DATA` | Read installed TV tuner channel lists and Electronic Program Guide (EPG) metadata provided by your TV tuner hardware. |
| `INTERNET` & `ACCESS_NETWORK_STATE` | Used solely for the optional **Phone Setup** feature, which hosts a private, temporary HTTP server on your local home Wi-Fi network (LAN) allowing you to reorder channels from a smartphone browser. No data ever leaves your local network. |

---

### 4. Local Phone Setup Server
When you open **Phone Setup** in the app:
- A lightweight HTTP server is launched locally on your TV box.
- The web page and API are accessible **only within your private local Wi-Fi network**.
- Access is protected by a high-entropy session token generated at runtime.
- The local server automatically stops when you close the Phone Setup screen or exit the application.
- No data is transmitted over the internet or to external servers.

---

### 5. Third-Party Services & Open Source Components
VividOrbit contains open-source components from:
- **Android Open Source Project (AOSP)** (Apache License 2.0)
- **Mochitif Engine** (DVB/IPTV Leanback playback library)
- **ZXing** (QR code generation for local LAN pairing)

None of these libraries collect or report analytics.

---

### 6. Children's Privacy
VividOrbit does not collect personal information from any user, including children under the age of 13.

---

### 7. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. Any updates will be posted with a revised "Last Updated" date.

---

### 8. Contact Us
If you have any questions or suggestions regarding our Privacy Policy or open-source software, please contact us at:
**appsvorynlabs@gmail.com**
