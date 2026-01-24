const MAILPIT_URL = process.env.MAILPIT_URL || 'http://localhost:8025';

interface MailpitMessage {
  ID: string;
  MessageID: string;
  From: { Address: string; Name: string };
  To: Array<{ Address: string; Name: string }>;
  Subject: string;
  Date: string;
  Text: string;
  HTML: string;
}

interface MailpitMessagesResponse {
  total: number;
  unread: number;
  count: number;
  messages: Array<{
    ID: string;
    MessageID: string;
    From: { Address: string; Name: string };
    To: Array<{ Address: string; Name: string }>;
    Subject: string;
    Date: string;
    Snippet: string;
  }>;
}

export class MailPit {
  async deleteAllMessages(): Promise<void> {
    await fetch(`${MAILPIT_URL}/api/v1/messages`, {
      method: 'DELETE',
    });
  }

  async getMessages(): Promise<MailpitMessagesResponse> {
    const response = await fetch(`${MAILPIT_URL}/api/v1/messages`);
    if (!response.ok) {
      throw new Error(`Failed to get messages: ${response.status}`);
    }
    return response.json();
  }

  async getMessage(id: string): Promise<MailpitMessage> {
    const response = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`);
    if (!response.ok) {
      throw new Error(`Failed to get message: ${response.status}`);
    }
    return response.json();
  }

  async waitForMessage(
    toEmail: string,
    subjectContains?: string,
    timeoutMs: number = 30000
  ): Promise<MailpitMessage> {
    const startTime = Date.now();

    while (Date.now() - startTime < timeoutMs) {
      const response = await this.getMessages();

      for (const msg of response.messages) {
        const matchesTo = msg.To.some(
          (t) => t.Address.toLowerCase() === toEmail.toLowerCase()
        );
        const matchesSubject =
          !subjectContains ||
          msg.Subject.toLowerCase().includes(subjectContains.toLowerCase());

        if (matchesTo && matchesSubject) {
          return this.getMessage(msg.ID);
        }
      }

      // Wait 500ms before checking again
      await new Promise((resolve) => setTimeout(resolve, 500));
    }

    throw new Error(
      `Timeout waiting for email to ${toEmail}${subjectContains ? ` with subject containing "${subjectContains}"` : ''}`
    );
  }

  extractOtpCode(message: MailpitMessage): string | null {
    // Try to extract OTP from text body
    // The OTP code is typically displayed prominently in the email
    const text = message.Text || message.HTML;

    // Look for a code pattern - our default alphabet is 23456789ABCDEFGHJKLMNPQRSTUVWXYZ
    // and default length is 6
    const codePattern = /\b([23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4,8})\b/g;
    const matches = text.match(codePattern);

    if (matches && matches.length > 0) {
      // Return the first match that looks like an OTP (typically 6 chars)
      const otpMatch = matches.find((m) => m.length >= 4 && m.length <= 8);
      return otpMatch || null;
    }

    return null;
  }
}

export const mailpit = new MailPit();
