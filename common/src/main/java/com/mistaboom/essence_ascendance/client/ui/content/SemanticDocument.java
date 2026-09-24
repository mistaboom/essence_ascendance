package com.mistaboom.essence_ascendance.client.ui.content;

import com.mistaboom.essence_ascendance.client.ui.data.ReadOnlyDataTable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Render-independent document composition. The same semantic components feed
 * the article viewport and global search without constructing a screen or a
 * duplicate search-only article database.
 */
public record SemanticDocument(Component title, List<Block> blocks) {
    public SemanticDocument {
        Objects.requireNonNull(title, "Document title");
        blocks = List.copyOf(blocks);
    }

    public enum HeadingLevel { SECTION, SUBSECTION }
    public enum CalloutKind { NOTE, WARNING, TIP }
    public enum RequirementStatus { MET, UNMET, INFORMATION }
    public enum LinkRelation { RELATED, PREVIOUS, NEXT }

    public sealed interface Block permits Heading, Paragraph, OrderedSteps, Callout,
            Illustration, ItemIllustration, Icon, ItemRow, StatRows, Requirements, Table, Links {
        List<Component> searchableText();
    }

    /** Compact item-model identity used by articles without requiring custom art. */
    public record Icon(ResourceLocation resource, Component label) implements Block {
        public Icon { Objects.requireNonNull(resource); Objects.requireNonNull(label); }
        @Override public List<Component> searchableText() { return List.of(label); }
    }

    /** Related item variants, kept together in order and wrapped only when necessary. */
    public record ItemRow(List<ItemPresentation> items) implements Block {
        public ItemRow { items = List.copyOf(items); }
        @Override public List<Component> searchableText() { return items.stream().map(ItemPresentation::label).toList(); }
    }

    public record Heading(HeadingLevel level, Component text) implements Block {
        public Heading { Objects.requireNonNull(level); Objects.requireNonNull(text); }
        @Override public List<Component> searchableText() { return List.of(text); }
    }

    public record Paragraph(Component text) implements Block {
        public Paragraph { Objects.requireNonNull(text); }
        @Override public List<Component> searchableText() { return List.of(text); }
    }

    public record OrderedSteps(List<Component> steps) implements Block {
        public OrderedSteps { steps = List.copyOf(steps); }
        @Override public List<Component> searchableText() { return steps; }
    }

    public record Callout(CalloutKind kind, Component title, Component text) implements Block {
        public Callout { Objects.requireNonNull(kind); Objects.requireNonNull(title); Objects.requireNonNull(text); }
        @Override public List<Component> searchableText() { return List.of(title, text); }
    }

    /** Resource is an item ID; the renderer resolves the current registered model at draw time. */
    public record Illustration(ResourceLocation resource, Component caption, int preferredWidth,
                               int preferredHeight) implements Block {
        public Illustration {
            Objects.requireNonNull(resource); Objects.requireNonNull(caption);
            if (preferredWidth < 1 || preferredHeight < 1) throw new IllegalArgumentException("Invalid illustration size");
        }
        @Override public List<Component> searchableText() { return List.of(caption); }
    }

    /** A large registered item figure whose component data selects the real model variant. */
    public record ItemIllustration(ItemPresentation item, int preferredWidth,
                                   int preferredHeight) implements Block {
        public ItemIllustration {
            Objects.requireNonNull(item);
            if (preferredWidth < 1 || preferredHeight < 1) throw new IllegalArgumentException("Invalid illustration size");
        }
        @Override public List<Component> searchableText() { return List.of(item.label(), item.caption()); }
    }

    public record StatRow(Component label, Component value) {
        public StatRow { Objects.requireNonNull(label); Objects.requireNonNull(value); }
    }

    public record StatRows(List<StatRow> rows) implements Block {
        public StatRows { rows = List.copyOf(rows); }
        @Override public List<Component> searchableText() {
            List<Component> text = new ArrayList<>();
            rows.forEach(row -> { text.add(row.label()); text.add(row.value()); });
            return List.copyOf(text);
        }
    }

    public record Requirement(Component label, Component detail, RequirementStatus status) {
        public Requirement { Objects.requireNonNull(label); Objects.requireNonNull(detail); Objects.requireNonNull(status); }
    }

    public record Requirements(List<Requirement> rows) implements Block {
        public Requirements { rows = List.copyOf(rows); }
        @Override public List<Component> searchableText() {
            List<Component> text = new ArrayList<>();
            rows.forEach(row -> { text.add(row.label()); text.add(row.detail()); });
            return List.copyOf(text);
        }
    }

    public enum TableLayoutPolicy {
        /** The enclosing article owns scrolling and the table expands to every row. */
        ARTICLE_FLOW,
        /** The table owns one bounded, virtualized results viewport. */
        BOUNDED_RESULTS
    }

    public record Table<R>(ReadOnlyDataTable<R> data, TableLayoutPolicy layoutPolicy) implements Block {
        public Table(ReadOnlyDataTable<R> data) { this(data, TableLayoutPolicy.BOUNDED_RESULTS); }
        public Table(ReadOnlyDataTable<R> data, boolean expanded) {
            this(data, expanded ? TableLayoutPolicy.ARTICLE_FLOW : TableLayoutPolicy.BOUNDED_RESULTS);
        }
        public Table { Objects.requireNonNull(data); Objects.requireNonNull(layoutPolicy); }
        public boolean expanded() { return layoutPolicy == TableLayoutPolicy.ARTICLE_FLOW; }
        @Override public List<Component> searchableText() {
            List<Component> text = new ArrayList<>();
            data.columns().forEach(column -> text.add(column.header()));
            data.rows().forEach(row -> data.columns().forEach(column -> text.add(column.display(row.value()))));
            return List.copyOf(text);
        }
    }

    public record Link(String target, Component label, LinkRelation relation) {
        public Link {
            if (target == null || target.isBlank()) throw new IllegalArgumentException("Content link target cannot be blank");
            Objects.requireNonNull(label); Objects.requireNonNull(relation);
        }
    }

    public record Links(List<Link> links) implements Block {
        public Links { links = List.copyOf(links); }
        @Override public List<Component> searchableText() { return links.stream().map(Link::label).toList(); }
    }

    public List<Component> searchableText() {
        List<Component> text = new ArrayList<>();
        text.add(title);
        blocks.forEach(block -> text.addAll(block.searchableText()));
        return List.copyOf(text);
    }
}
