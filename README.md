SmtpProxy - README

Overview
- `SmtpProxy.java` implements a simple SMTP proxy listening on port 55555 by default.
- The proxy forwards SMTP commands between a mail client and a real SMTP server (default localhost:25).
- When the proxy forwards message content (after the `DATA` command) it filters "ungood" words and replaces them with configured replacements. Before terminating the DATA block it injects a disclaimer line to the SMTP server.

Build and run
- Compile:
```bash
javac SmtpProxy.java
```
- Run:
```bash
java SmtpProxy
```
The proxy listens on port 55555 (change the constant in the source if you need another port).

Client configuration (example)
- SMTP server (outgoing): set to the host running the proxy, port 55555. Disable TLS/SSL in the client.
- POP3 server (incoming): set to the real mail server's hostname or IP and port 110 (POP3, no TLS) so the client retrieves mail from the real server.

Installing SMTP/POP3 on a Linux VM (Debian/Ubuntu)
- Install Postfix (SMTP) and Dovecot (POP3):
```bash
sudo apt update
sudo apt install -y postfix dovecot-pop3d
```
- Postfix listens on port 25 by default. Dovecot POP3 listens on port 110 by default.
- Configure users/mailboxes according to your distro docs. Ensure the SMTP server accepts connections from the proxy (localhost or network).

Testing manually
- You can test SMTP via `telnet` or `nc` to the proxy on port 55555 and speak SMTP commands. Example:
```bash
telnet 127.0.0.1 55555
EHLO example.com
MAIL FROM:<sender@example.com>
RCPT TO:<recipient@example.com>
DATA
Subject: Test
This contains badword which should be filtered.
.
QUIT
```
- The proxy will forward commands to the real SMTP server and inject the disclaimer before the terminating `.`.

Notes
- The proxy performs simple word-boundary, case-insensitive replacements defined in the `REPLACEMENTS` map inside `SmtpProxy.java`.
- This implementation does not implement TLS/SSL. Configure your client not to use encryption for SMTP when testing.
- If you want a full mail stack in a VM quickly, consider using a prebuilt Docker mailserver image (for example `mailserver/docker-mailserver`) and configure Postfix/Dovecot there.

