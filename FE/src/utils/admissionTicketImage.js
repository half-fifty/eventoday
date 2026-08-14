const IMAGE_WIDTH = 900;
const IMAGE_HEIGHT = 1320;
const CARD_RADIUS = 36;
const QR_SIZE = 470;
const PNG_TYPE = "image/png";

const formatDate = (value) => {
  if (!value) return "-";
  return new Date(value).toLocaleString("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  });
};

const sanitizeFileSegment = (value) => {
  const sanitized = String(value || "")
    .normalize("NFKC")
    .replace(/[\\/:*?"<>|]/g, "-")
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 48);

  return sanitized || "ticket";
};

export const createAdmissionTicketFilename = (ticket) => {
  const eventName = sanitizeFileSegment(ticket?.eventName);
  return `eventoday-ticket-${eventName}.png`;
};

const loadImage = (src) =>
  new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("QR image load failed"));
    image.src = src;
  });

const canvasToBlob = (canvas) =>
  new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob);
        return;
      }
      reject(new Error("PNG generation failed"));
    }, PNG_TYPE);
  });

const drawRoundRect = (context, x, y, width, height, radius) => {
  context.beginPath();
  context.moveTo(x + radius, y);
  context.arcTo(x + width, y, x + width, y + height, radius);
  context.arcTo(x + width, y + height, x, y + height, radius);
  context.arcTo(x, y + height, x, y, radius);
  context.arcTo(x, y, x + width, y, radius);
  context.closePath();
};

const drawCenteredWrappedText = (context, text, x, y, maxWidth, lineHeight, maxLines) => {
  const words = String(text || "-").split(/\s+/);
  const lines = [];
  let currentLine = "";

  const splitOversizedToken = (token) => {
    const chunks = [];
    let currentChunk = "";
    Array.from(token).forEach((character) => {
      const nextChunk = currentChunk + character;
      if (!currentChunk || context.measureText(nextChunk).width <= maxWidth) {
        currentChunk = nextChunk;
        return;
      }
      chunks.push(currentChunk);
      currentChunk = character;
    });
    if (currentChunk) chunks.push(currentChunk);
    return chunks;
  };

  const pushToken = (token) => {
    const parts = context.measureText(token).width > maxWidth
      ? splitOversizedToken(token)
      : [token];

    parts.forEach((part) => {
      const nextLine = currentLine ? `${currentLine} ${part}` : part;
      if (context.measureText(nextLine).width <= maxWidth) {
        currentLine = nextLine;
        return;
      }
      if (currentLine) lines.push(currentLine);
      currentLine = part;
    });
  };

  words.forEach((word) => {
    pushToken(word);
  });
  if (currentLine) lines.push(currentLine);

  const visibleLines = lines.slice(0, maxLines);
  if (lines.length > maxLines && visibleLines.length > 0) {
    const lastIndex = visibleLines.length - 1;
    let lastLine = visibleLines[lastIndex];
    while (context.measureText(`${lastLine}...`).width > maxWidth && lastLine.length > 0) {
      lastLine = lastLine.slice(0, -1);
    }
    visibleLines[lastIndex] = `${lastLine}...`;
  }

  visibleLines.forEach((line, index) => {
    context.fillText(line, x, y + index * lineHeight);
  });
  return visibleLines.length * lineHeight;
};

export const createAdmissionTicketImageBlob = async ({ ticket, qrImageUrl }) => {
  if (!ticket || !qrImageUrl) {
    throw new Error("Ticket or QR image is missing");
  }

  const qrImage = await loadImage(qrImageUrl);
  const canvas = document.createElement("canvas");
  canvas.width = IMAGE_WIDTH;
  canvas.height = IMAGE_HEIGHT;

  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("Canvas is not available");
  }

  context.fillStyle = "#f5f7f6";
  context.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

  drawRoundRect(context, 48, 48, IMAGE_WIDTH - 96, IMAGE_HEIGHT - 96, CARD_RADIUS);
  context.fillStyle = "#ffffff";
  context.fill();
  context.strokeStyle = "#d6dedb";
  context.lineWidth = 3;
  context.stroke();

  context.textAlign = "center";
  context.fillStyle = "#0f766e";
  context.font = "700 46px Arial, sans-serif";
  context.fillText("Eventoday", IMAGE_WIDTH / 2, 150);

  context.fillStyle = "#111827";
  context.font = "700 52px Arial, sans-serif";
  drawCenteredWrappedText(context, ticket.eventName, IMAGE_WIDTH / 2, 275, 700, 64, 3);

  const qrX = (IMAGE_WIDTH - QR_SIZE) / 2;
  const qrY = 510;
  drawRoundRect(context, qrX - 34, qrY - 34, QR_SIZE + 68, QR_SIZE + 68, 28);
  context.fillStyle = "#ffffff";
  context.fill();
  context.strokeStyle = "#e5e7eb";
  context.lineWidth = 2;
  context.stroke();
  context.drawImage(qrImage, qrX, qrY, QR_SIZE, QR_SIZE);

  context.textAlign = "left";
  context.fillStyle = "#6b7280";
  context.font = "600 27px Arial, sans-serif";
  context.fillText("발급일", 130, 1100);
  context.fillText("상태", 130, 1165);

  context.fillStyle = "#111827";
  context.font = "700 30px Arial, sans-serif";
  context.fillText(formatDate(ticket.issuedAt), 330, 1100);
  context.fillText(ticket.admissionTicketStatus || ticket.status || "-", 330, 1165);

  context.textAlign = "center";
  context.fillStyle = "#0f766e";
  context.font = "700 30px Arial, sans-serif";
  context.fillText("입장 시 QR을 제시해주세요", IMAGE_WIDTH / 2, 1268);

  return canvasToBlob(canvas);
};

const downloadBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
};

const canShareFile = (file) =>
  typeof navigator !== "undefined"
  && typeof navigator.canShare === "function"
  && typeof navigator.share === "function"
  && navigator.canShare({ files: [file] });

export const shareOrDownloadAdmissionTicketImage = async ({ ticket, qrImageUrl }) => {
  const blob = await createAdmissionTicketImageBlob({ ticket, qrImageUrl });
  const filename = createAdmissionTicketFilename(ticket);
  const file = new File([blob], filename, { type: PNG_TYPE });

  if (canShareFile(file)) {
    try {
      await navigator.share({
        files: [file],
        title: `${ticket.eventName || "Eventoday"} 입장권`,
        text: "Eventoday 입장권 이미지",
      });
      return { action: "shared", filename };
    } catch (error) {
      if (error?.name === "AbortError") {
        return { action: "cancelled", filename };
      }
      throw error;
    }
  }

  downloadBlob(blob, filename);
  return { action: "downloaded", filename };
};
