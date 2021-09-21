package com.checkmarx.intellij.components;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

/**
 * Label with link style. Performs the supplied {@link MouseEvent} consumer when clicked.
 */
public class LinkLabel extends JBLabel {

    private static final Logger LOGGER = Utils.getLogger(LinkLabel.class);

    public LinkLabel(@NotNull Resource resource, Consumer<MouseEvent> onClick) {
        this(Bundle.message(resource), onClick);
    }

    public LinkLabel(@NotNull String text, Consumer<MouseEvent> onClick) {
        super(String.format(Constants.HELP_HTML, text));

        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                onClick.accept(e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                setText(String.format(Constants.HELP_HTML_U, text));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                setText(String.format(Constants.HELP_HTML, text));
            }
        });
    }

    /**
     * Build label for documentation link.
     * Changes to underlined link with hand cursor when hovered.
     *
     * @return label link component
     */
    public static JBLabel buildDocLinkLabel(String link, Resource resource) {
        return new LinkLabel(resource, getMouseEventConsumer(link));
    }

    /**
     * Build label for documentation link.
     * Changes to underlined link with hand cursor when hovered.
     *
     * @return label link component
     */
    public static JBLabel buildDocLinkLabel(String link, String label) {
        return new LinkLabel(label, getMouseEventConsumer(link));
    }

    @NotNull
    private static Consumer<MouseEvent> getMouseEventConsumer(String link) {
        return mouseEvent -> {
            try {
                Desktop.getDesktop().browse(new URI(link));
            } catch (URISyntaxException | IOException ex) {
                LOGGER.error(ex);
            }
        };
    }
}
