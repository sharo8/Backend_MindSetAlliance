package com.mindsetalliance.core.reports;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class ReportPdfTheme {

    static final Color NAVY = new Color(0x16, 0x21, 0x5c);
    static final Color GOLD = new Color(0xc4, 0x92, 0x14);
    static final Color GOLD_SOFT = new Color(0xf6, 0xd0, 0x6a);
    static final Color ZEBRA = new Color(0xf8, 0xfa, 0xfc);
    static final Color MUTED = new Color(0x64, 0x74, 0x8b);
    static final Color LINE = new Color(0xe2, 0xe8, 0xf0);
    static final Color SUCCESS = new Color(0x16, 0xa3, 0x4a);
    static final Color DANGER = new Color(0xdc, 0x26, 0x26);
    static final Color WHITE = Color.WHITE;

    static final Map<String, Color> ROLE_COLORS = Map.ofEntries(
            Map.entry("FINANCE", new Color(0x0f, 0x76, 0x6e)),
            Map.entry("MARKETING", new Color(0x7c, 0x3a, 0xed)),
            Map.entry("SUPPORT", new Color(0xd9, 0x77, 0x06)),
            Map.entry("DEV", new Color(0x25, 0x63, 0xeb)),
            Map.entry("RH", new Color(0x16, 0xa3, 0x4a)),
            Map.entry("COMMERCIAL", GOLD),
            Map.entry("DIRECTION", NAVY),
            Map.entry("CONSEIL_ADMINISTRATION", new Color(0x1e, 0x3a, 0x8a)),
            Map.entry("JURIDIQUE", MUTED),
            Map.entry("ADMIN_SYSTEME", DANGER)
    );

    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);

    private ReportPdfTheme() {
    }

    static byte[] build(String title, String author, boolean landscape, Consumer<Document> body) {
        Rectangle size = landscape ? PageSize.A4.rotate() : PageSize.A4;
        Document document = new Document(size, 36, 36, 72, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            HeaderFooter event = new HeaderFooter(title, STAMP.format(ZonedDateTime.now()));
            writer.setPageEvent(event);
            document.open();
            cover(document, title, author);
            document.newPage();
            event.contentStarted = true;
            body.accept(document);
            document.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF generation failed", ex);
        }
        return out.toByteArray();
    }

    static void cover(Document document, String title, String author) {
        try {
            Image logo = logo(72);
            if (logo != null) {
                logo.setAlignment(Element.ALIGN_CENTER);
                document.add(logo);
            }
            Paragraph brand = new Paragraph("MINDSET ALLIANCE", font(11, Font.BOLD, GOLD));
            brand.setAlignment(Element.ALIGN_CENTER);
            brand.setSpacingBefore(8);
            document.add(brand);
            Paragraph workspace = new Paragraph("MA WORKSPACE", font(9, Font.NORMAL, MUTED));
            workspace.setAlignment(Element.ALIGN_CENTER);
            workspace.setSpacingAfter(28);
            document.add(workspace);

            PdfPTable rule = new PdfPTable(1);
            rule.setWidthPercentage(100);
            PdfPCell bar = new PdfPCell();
            bar.setFixedHeight(4);
            bar.setBackgroundColor(GOLD);
            bar.setBorder(Rectangle.NO_BORDER);
            rule.addCell(bar);
            document.add(rule);

            Paragraph heading = new Paragraph(title.toUpperCase(ReportI18n.english() ? Locale.UK : Locale.FRANCE), font(22, Font.BOLD, NAVY));
            heading.setAlignment(Element.ALIGN_CENTER);
            heading.setSpacingBefore(28);
            heading.setSpacingAfter(16);
            document.add(heading);

            Paragraph meta = new Paragraph(
                    ReportI18n.t("cover.generated") + STAMP.format(ZonedDateTime.now()) + "\n" + ReportI18n.t("cover.by") + author,
                    font(11, Font.NORMAL, MUTED));
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(36);
            document.add(meta);

            Paragraph confidential = new Paragraph(ReportI18n.t("cover.confidential"), font(9, Font.ITALIC, DANGER));
            confidential.setAlignment(Element.ALIGN_CENTER);
            document.add(confidential);
        } catch (DocumentException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void h2(Document document, String text) {
        Paragraph p = new Paragraph(text.toUpperCase(ReportI18n.english() ? Locale.UK : Locale.FRANCE), font(11, Font.BOLD, NAVY));
        p.setSpacingBefore(14);
        p.setSpacingAfter(8);
        try {
            document.add(p);
        } catch (DocumentException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void paragraph(Document document, String text) {
        try {
            Paragraph p = new Paragraph(text, font(9, Font.NORMAL, MUTED));
            p.setSpacingAfter(8);
            document.add(p);
        } catch (DocumentException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static PdfPTable kpiRow(String[] labels, String[] values) {
        PdfPTable table = new PdfPTable(values.length);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        for (int i = 0; i < values.length; i++) {
            PdfPTable inner = new PdfPTable(1);
            PdfPCell top = new PdfPCell(new Phrase(labels[i], font(7, Font.BOLD, GOLD)));
            top.setBorder(Rectangle.NO_BORDER);
            top.setPadding(4);
            PdfPCell value = new PdfPCell(new Phrase(values[i], font(16, Font.BOLD, NAVY)));
            value.setBorder(Rectangle.NO_BORDER);
            value.setPadding(4);
            inner.addCell(top);
            inner.addCell(value);
            PdfPCell wrap = new PdfPCell(inner);
            wrap.setBorderColor(GOLD);
            wrap.setBorderWidth(1f);
            wrap.setPadding(6);
            wrap.setBackgroundColor(new Color(0xff, 0xfb, 0xf0));
            table.addCell(wrap);
        }
        return table;
    }

    static PdfPCell head(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(8, Font.BOLD, WHITE)));
        cell.setBackgroundColor(NAVY);
        cell.setPadding(6);
        cell.setBorderColor(NAVY);
        return cell;
    }

    static PdfPCell body(String text, int row, boolean zebra) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null || text.isBlank() ? "—" : text, font(8, Font.NORMAL, NAVY)));
        cell.setPadding(5);
        cell.setBorderColor(LINE);
        if (zebra && row % 2 == 1) {
            cell.setBackgroundColor(ZEBRA);
        }
        return cell;
    }

    static PdfPCell badge(String text, Color background, int row) {
        Font f = font(7, Font.BOLD, WHITE);
        Chunk chunk = new Chunk("  " + text + "  ", f);
        chunk.setBackground(background, 2, 1.5f, 2, 1.5f);
        PdfPCell cell = new PdfPCell(new Phrase(chunk));
        cell.setPadding(5);
        cell.setBorderColor(LINE);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (row % 2 == 1) {
            cell.setBackgroundColor(ZEBRA);
        }
        return cell;
    }

    static Color roleColor(String role) {
        if (role == null) {
            return GOLD;
        }
        return ROLE_COLORS.getOrDefault(role.toUpperCase(Locale.ROOT), GOLD);
    }

    static Color statutColor(String statut) {
        if (statut != null && "ACTIF".equalsIgnoreCase(statut)) {
            return SUCCESS;
        }
        return DANGER;
    }

    static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    static Image logo(float size) {
        try (InputStream in = ReportPdfTheme.class.getResourceAsStream("/branding/logo-mindset-alliance.jpg")) {
            if (in == null) {
                return null;
            }
            Image image = Image.getInstance(in.readAllBytes());
            image.scaleToFit(size, size);
            return image;
        } catch (Exception ex) {
            return null;
        }
    }

    static class HeaderFooter extends PdfPageEventHelper {
        private final String title;
        private final String generatedAt;
        private PdfTemplate total;
        boolean contentStarted;

        HeaderFooter(String title, String generatedAt) {
            this.title = title;
            this.generatedAt = generatedAt;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float left = document.left();
            float right = document.right();
            float top = document.top() + 28;
            float bottom = document.bottom() - 22;

            if (contentStarted) {
                try {
                    Image mark = logo(16);
                    if (mark != null) {
                        mark.setAbsolutePosition(left, top - 4);
                        cb.addImage(mark);
                    }
                } catch (Exception ignored) {
                    // logo optional on inner pages
                }
                cb.beginText();
                cb.setFontAndSize(base(), 8);
                cb.setColorFill(NAVY);
                cb.setTextMatrix(left + 22, top);
                cb.showText("Mindset Alliance  ·  " + title);
                cb.endText();
                cb.setColorStroke(GOLD);
                cb.setLineWidth(0.8f);
                cb.moveTo(left, top - 8);
                cb.lineTo(right, top - 8);
                cb.stroke();
            }

            cb.setColorStroke(GOLD);
            cb.setLineWidth(0.6f);
            cb.moveTo(left, bottom + 14);
            cb.lineTo(right, bottom + 14);
            cb.stroke();

            cb.beginText();
            cb.setFontAndSize(base(), 7);
            cb.setColorFill(MUTED);
            cb.setTextMatrix(left, bottom);
            cb.showText(ReportI18n.t("cover.confidential") + "  ·  " + generatedAt);
            cb.endText();

            String pageLabel = ReportI18n.t("footer.page") + writer.getPageNumber() + " / ";
            cb.beginText();
            cb.setFontAndSize(base(), 7);
            cb.setColorFill(MUTED);
            float pageX = right - 70;
            cb.setTextMatrix(pageX, bottom);
            cb.showText(pageLabel);
            cb.endText();
            cb.addTemplate(total, pageX + base().getWidthPoint(pageLabel, 7), bottom);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(base(), 7);
            total.setColorFill(MUTED);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber()));
            total.endText();
        }

        private static BaseFont base() {
            try {
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
