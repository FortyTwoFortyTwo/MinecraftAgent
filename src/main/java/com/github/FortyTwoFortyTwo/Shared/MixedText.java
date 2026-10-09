package com.github.FortyTwoFortyTwo.Shared;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Text where only some parts could have been written by players, e.g. command feedback naming a player or a custom named item.
 * Parts are either a String the agent can read, or PlayerText it only gets a placeholder for.
 */
public record MixedText(List<Serializable> parts) implements Serializable {

    // Literal text without letters can't spell out instructions, e.g. coordinates, counts and separators
    private static final Pattern SAFE_LITERAL = Pattern.compile("[\\s\\d.,:;+\\-\\[\\](){}/%]*");
    // Stands in for a translation's arguments, so its template can be split around them
    private static final String MARK = "";
    private static final Pattern ARGUMENT = Pattern.compile(MARK + "(\\d+)" + MARK);
    // A message hidden in more pieces than this, e.g. /data get's NBT, is hidden as a whole instead, so it doesn't flood the agent with tags
    private static final int MAX_HIDDEN = 10;

    /** Command feedback, one message per line */
    public static MixedText of(List<Component> messages) {
        List<Serializable> parts = new ArrayList<>();

        for (Component message : messages) {
            if (!parts.isEmpty())
                add(parts, "\n");

            List<Serializable> line = new ArrayList<>();
            walk(message, line);

            if (line.stream().filter(PlayerText.class::isInstance).count() > MAX_HIDDEN)
                add(parts, PlayerText.of(plain(message)));
            else
                line.forEach(part -> add(parts, part));
        }

        return new MixedText(parts);
    }

    private static void walk(Component component, List<Serializable> parts) {
        switch (component) {
            case TextComponent text -> {
                if (SAFE_LITERAL.matcher(text.content()).matches())
                    add(parts, text.content());
                else
                    add(parts, PlayerText.of(text.content()));
            }
            case TranslatableComponent translatable -> translate(translatable, parts);
            // Selectors, scores, NBT and the like resolve to text from the world
            default -> add(parts, PlayerText.of(plain(component.children(List.of()))));
        }

        component.children().forEach(child -> walk(child, parts));
    }

    /** The game's own translation is shown, its arguments are walked like any other component */
    private static void translate(TranslatableComponent translatable, List<Serializable> parts) {
        List<TranslationArgument> arguments = translatable.arguments();
        List<Component> marks = IntStream.range(0, arguments.size()).mapToObj(i -> (Component) Component.text(MARK + i + MARK)).toList();
        // No fallback, as anyone who can name an item can set one
        String template = plain(Component.translatable(translatable.key(), marks));

        // A key the server doesn't know renders as itself, and could be anything a player wrote
        if (template.equals(translatable.key())) {
            add(parts, PlayerText.of(plain(translatable.children(List.of()))));
            return;
        }

        Matcher matcher = ARGUMENT.matcher(template);
        int last = 0;
        while (matcher.find()) {
            add(parts, template.substring(last, matcher.start()));
            walk(arguments.get(Integer.parseInt(matcher.group(1))).asComponent(), parts);
            last = matcher.end();
        }

        add(parts, template.substring(last));
    }

    /** Joins neighbouring parts of the same kind, so hidden text gets as few placeholders as possible */
    private static void add(List<Serializable> parts, Serializable part) {
        if (part instanceof String text && text.isEmpty())
            return;

        if (!parts.isEmpty()) {
            Serializable previous = parts.getLast();
            if (previous instanceof String before && part instanceof String text) {
                parts.set(parts.size() - 1, before + text);
                return;
            }

            if (previous instanceof PlayerText before && part instanceof PlayerText text) {
                parts.set(parts.size() - 1, PlayerText.of(before.text() + text.text()));
                return;
            }
        }

        parts.add(part);
    }

    /** Paper renders translations here in the server's language */
    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
