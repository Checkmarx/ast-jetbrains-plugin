package com.checkmarx.intellij.components;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.HyperlinkLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
public class CxLinkLabel extends HyperlinkLabel {

    private static final Logger LOGGER = Utils.getLogger(CxLinkLabel.class);

    public CxLinkLabel(@NotNull Resource resource, Consumer<MouseEvent> onClick) {
        this(Bundle.message(resource), onClick);
    }

    public CxLinkLabel(@NotNull String text, Consumer<MouseEvent> onClick) {
        super(text);

        addMouseListener(new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                onClick.accept(e);
            }
        });
    }

    /**
     * Build label for documentation link.
     * Changes to underlined link with hand cursor when hovered.
     *
     * @return label link component
     */
    public static JComponent buildDocLinkLabel(String link, Resource resource) {
        return new CxLinkLabel(resource, getMouseEventConsumer(link));
    }

    /**
     * Build label for documentation link.
     * Changes to underlined link with hand cursor when hovered.
     *
     * @return label link component
     */
    public static JComponent buildDocLinkLabel(String link, String label) {
        return new CxLinkLabel(label, getMouseEventConsumer(link));
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
