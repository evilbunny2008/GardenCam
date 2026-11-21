# Security Policy

## Overview

Garden Cam (a fork of [ONVIF Camera](https://github.com/mretallack/cams), itself a fork of [Cams](https://github.com/vladpen/cams)) is designed with security and privacy as core principles. This document outlines the security measures, known issues, and recommendations for secure usage.

## Security Principles

1. **No Data Collection**: The app does not collect, transmit, or store any user data on external servers
2. **Local Storage Only**: All configuration and credentials are stored locally on the device
3. **No Analytics**: No Google Analytics, Firebase, or third-party tracking services
4. **No Cloud Services**: Direct camera connections only - no manufacturer cloud services
5. **Encrypted Credentials**: ONVIF credentials are encrypted with AES-256-GCM using an Android Keystore key

## Supported Versions

| Version | Supported          | Status                         |
|---------|--------------------|--------------------------------|
| 0.0.1   | :white_check_mark: | Current Garden Cam development |

Versions of Cams and ONVIF Camera (2.x) are separate apps and are not covered by this policy.

## Third-Party Dependencies

### Core Dependencies

#### VLC for Android (libvlc-all) - Version 3.7.7
- **License**: LGPL 2.1+ / GPL 2.0+
- **Purpose**: Video playback engine for RTSP/RTMP streams
- **Risk Level**: LOW - Only connect to trusted camera sources
- **Recommendation**: Keep up to date with libvlc-android releases

#### AndroidX Libraries
- **androidx.core:core-ktx**: 1.19.1
- **androidx.appcompat:appcompat**: 1.8.0
- **androidx.work:work-runtime-ktx**: 2.12.0
- **androidx.activity:activity-ktx**: 1.13.0
- **com.google.android.material:material**: 1.14.0
- **License**: Apache 2.0

#### Apache HttpClient Android - Version 4.3.5.1
- **License**: Apache 2.0
- **Purpose**: HTTP communication for ONVIF SOAP requests
- **Status**: ⚠️ **DEPRECATED** - Last updated 2014
- **Known Issues**: 
  - DefaultHttpClient class deprecated since 4.3
  - Lacks modern TLS 1.2/1.3 support by default
  - No longer receives security updates
- **Risk Level**: MEDIUM - Used only for local network ONVIF communication
- **Current Usage**: Limited to ONVIF device communication on local networks
- **Mitigation**: 
  - App uses HttpURLConnection for ONVIF SOAP (not HttpClient directly)
  - All ONVIF communication is on trusted local networks
  - TLS validation performed by Android platform
- **Recommendation**: Migrate to OkHttp or HttpClient 5.x in future release

#### JSch (SSH/SFTP) - Version 2.28.8
- **License**: BSD-style
- **Purpose**: SFTP file access for camera recordings
- **Status**: ✅ **CURRENT** - Maintained fork by mwiede
- **Security**: Actively maintained with security updates
- **Notes**: This is the community-maintained fork (com.github.mwiede:jsch)

#### Gson - Version 2.15.0
- **License**: Apache 2.0
- **Purpose**: JSON serialization for app settings
- **Status**: ✅ **CURRENT** - Latest stable release
- **Security**: No known vulnerabilities

## Security Features

### Credential Storage
- **ONVIF Credentials**: AES-256-GCM, key generated in and never leaving the Android Keystore.
  Credentials saved by older versions are re-encrypted on first load and the old key is deleted.
- **RTSP/SFTP Passwords**: AES-CBC with a key derived from the app's UID and install time
  (inherited from Cams). This deters casual reading but is not strong protection.
- **Settings Exports**: Credentials are written in a device-independent form, and the export
  file is protected by its own password.
- **Storage Location**: App-private storage

### Network Security
- **RTSP Streams**: Supports both TCP and UDP protocols
- **ONVIF Communication**: HTTP/HTTPS with WS-Security authentication
- **SFTP**: SSH-based secure file transfer
- **Certificate Validation**: Relies on Android platform TLS validation
- **Cleartext Traffic**: Permitted (required for IP camera protocols)

### Input Validation
- **URL Sanitization**: Basic validation and sanitization of camera URLs
- **XML Injection Protection**: ONVIF responses sanitized to remove script tags; encoder
  settings responses are parsed with DOCTYPE declarations (and so external entities) rejected
- **Length Limits**: Input fields limited to 1000 characters
- **Special Character Filtering**: Removes `<>&"'` from ONVIF inputs

### Android Security
- **Minimum SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 37 (Android 17)
- **Permissions**:
  - INTERNET (camera streams)
  - ACCESS_LOCAL_NETWORK (Android 17+: reaching cameras on the LAN; requested at runtime)
  - POST_NOTIFICATIONS (motion detection alerts)
  - RECORD_AUDIO (push-to-talk backchannel audio; requested at runtime)
  - VIBRATE (haptic feedback)
- **No Other Dangerous Permissions**: No access to contacts, location or the phone's camera

## Known Security Limitations

### 1. Deprecated HTTP Client (LOW RISK)
**Issue**: Apache HttpClient 4.3.5.1 is deprecated and lacks modern TLS support
**Impact**: Potential TLS downgrade attacks on ONVIF communication
**Mitigation**:
- ONVIF typically used on trusted local networks
- App uses HttpURLConnection (not HttpClient directly)
- Android platform provides TLS validation
**Recommendation**: Migrate to modern HTTP client in future release

### 2. Credential Encryption (RESOLVED)
ONVIF credentials previously used `AES/ECB/PKCS1Padding`, which isn't a valid AES transformation, so
they silently fell back to Base64. Garden Cam encrypts them with AES-256-GCM and an Android Keystore key.

### 3. RTSP/SFTP Password Encryption (MEDIUM RISK)
**Issue**: Passwords are encrypted with AES-CBC, but the key is derived from the app's UID and install time
**Impact**: Someone with a copy of the app's data and the install time could decrypt them
**Mitigation**:
- Android app storage is private by default
- Requires root access or device compromise to read
**Recommendation**: Move these to the Android Keystore, like ONVIF credentials

### 4. Cleartext Traffic Permitted (BY DESIGN)
**Issue**: App allows HTTP connections (not HTTPS only)
**Impact**: Traffic can be intercepted on network
**Justification**: 
- IP cameras typically use HTTP/RTSP (not HTTPS)
- ONVIF protocol commonly uses HTTP on local networks
- RTSP protocol does not support TLS
**Mitigation**: Use on trusted networks only

## Security Best Practices for Users

### Network Security
1. **Use Trusted Networks**: Only connect to cameras on trusted local networks
2. **Avoid Public WiFi**: Do not access cameras over public/untrusted WiFi
3. **Network Segmentation**: Place cameras on isolated VLAN if possible
4. **Firewall Rules**: Restrict camera access to local network only

### Camera Configuration
1. **Strong Passwords**: Use strong, unique passwords for camera credentials
2. **Disable UPnP**: Disable UPnP on cameras to prevent internet exposure
3. **Firmware Updates**: Keep camera firmware updated
4. **Disable Unused Services**: Disable cloud services and unused protocols

### App Usage
1. **Trusted Sources Only**: Only add cameras you own or trust
2. **Verify URLs**: Double-check camera URLs before saving
3. **Regular Updates**: Keep app updated to latest version
4. **Device Security**: Use device lock screen and encryption

### SFTP Security
1. **Dedicated User**: Create dedicated SFTP user with limited permissions
2. **Chroot Jail**: Configure SFTP server with chroot for isolation
3. **Key-Based Auth**: Use SSH keys instead of passwords when possible
4. **Read-Only Access**: Grant read-only access to recording directories

## Reporting Security Vulnerabilities

If you discover a security vulnerability, please report it responsibly:

1. **Do NOT** open a public GitHub issue
2. **Email**: Contact maintainer via GitHub profile
3. **Include**: 
   - Description of vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if available)

We will respond within 48 hours and work to address confirmed vulnerabilities promptly.

## Security Roadmap

### Planned Improvements
1. **Migrate HTTP Client**: Replace Apache HttpClient with OkHttp
2. **Encrypt RTSP/SFTP Passwords**: Use the same Keystore encryption as ONVIF credentials
3. **Certificate Pinning**: Add optional certificate pinning for ONVIF
4. **Security Audit**: Conduct third-party security audit

### Future Considerations
- Biometric authentication for app access
- Network traffic analysis/anomaly detection
- Secure element support for credential storage

## Compliance

### Privacy Regulations
- **GDPR Compliant**: No personal data collected or transmitted
- **CCPA Compliant**: No data sale or sharing
- **No Tracking**: No analytics or user tracking

### Open Source Licenses
All dependencies use permissive open-source licenses (MIT, Apache 2.0, BSD, LGPL). See [LICENSE.md](LICENSE.md) for full details.

## Security Audit History

| Date       | Auditor  | Scope             | Findings                                  | Status     |
|------------|----------|-------------------|-------------------------------------------|------------|
| 2026-01-24 | Internal | Dependency review | HttpClient deprecated, all others current | Documented |

## References

- [VLC for Android Releases](https://code.videolan.org/videolan/vlc-android/-/tags)
- [Apache HttpClient Migration Guide](https://hc.apache.org/httpcomponents-client-5.6.x/migration-guide/)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)

## Changelog

- **2026-11-21**: Updated for the Garden Cam fork
  - ONVIF credentials moved to AES-256-GCM with an Android Keystore key
  - Corrected the description of RTSP/SFTP password storage
  - Updated dependency versions, target SDK and permissions
  - Removed test dependencies (test suites removed)
- **2026-01-24**: Initial security policy created
  - Documented all dependencies and versions
  - Clarified VLC Android 3.6.5 is newer than desktop 3.0.22
  - Noted Apache HttpClient deprecation
  - Documented encryption methods and limitations

---

*Last Updated: 2026-11-21*  
*Version: 0.0.1*
