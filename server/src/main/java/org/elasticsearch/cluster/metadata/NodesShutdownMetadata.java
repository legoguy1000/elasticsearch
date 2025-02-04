/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.AbstractDiffable;
import org.elasticsearch.cluster.Diff;
import org.elasticsearch.cluster.DiffableUtils;
import org.elasticsearch.cluster.NamedDiff;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contains the data about nodes which are currently configured to shut down, either permanently or temporarily.
 *
 * Stored in the cluster state as custom metadata.
 */
public class NodesShutdownMetadata implements Metadata.Custom {
    public static final String TYPE = "node_shutdown";
    public static final Version NODE_SHUTDOWN_VERSION = Version.V_8_0_0;

    private static final ParseField NODES_FIELD = new ParseField("nodes");

    @SuppressWarnings("unchecked")
    public static final ConstructingObjectParser<NodesShutdownMetadata, Void> PARSER = new ConstructingObjectParser<>(TYPE, a -> {
        final Map<String, SingleNodeShutdownMetadata> nodes = ((List<SingleNodeShutdownMetadata>) a[0]).stream()
            .collect(Collectors.toMap(SingleNodeShutdownMetadata::getNodeId, Function.identity()));
        return new NodesShutdownMetadata(nodes);
    });

    static {
        PARSER.declareNamedObjects(
            ConstructingObjectParser.constructorArg(),
            (p, c, n) -> SingleNodeShutdownMetadata.parse(p),
            v -> { throw new IllegalArgumentException("ordered " + NODES_FIELD.getPreferredName() + " are not supported"); },
            NODES_FIELD
        );
    }

    public static NodesShutdownMetadata fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    public static NamedDiff<Metadata.Custom> readDiffFrom(StreamInput in) throws IOException {
        return new NodeShutdownMetadataDiff(in);
    }

    private final Map<String, SingleNodeShutdownMetadata> nodes;

    public NodesShutdownMetadata(Map<String, SingleNodeShutdownMetadata> nodes) {
        this.nodes = nodes;
    }

    public NodesShutdownMetadata(StreamInput in) throws IOException {
        this.nodes = in.readMap(StreamInput::readString, SingleNodeShutdownMetadata::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(nodes, StreamOutput::writeString, (outStream, v) -> v.writeTo(outStream));
    }

    /**
     * Retrieve the data about nodes which are currently in the process of shutting down.
     * @return A map of node IDs to information about the node's shutdown status.
     */
    public Map<String, SingleNodeShutdownMetadata> getPerNodeInfo() {
        return Collections.unmodifiableMap(nodes);
    }

    @Override
    public Diff<Metadata.Custom> diff(Metadata.Custom previousState) {
        return new NodeShutdownMetadataDiff((NodesShutdownMetadata) previousState, this);
    }

    @Override
    public EnumSet<Metadata.XContentContext> context() {
        return Metadata.ALL_CONTEXTS;
    }

    @Override
    public String getWriteableName() {
        return TYPE;
    }

    @Override
    public Version getMinimalSupportedVersion() {
        return NODE_SHUTDOWN_VERSION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ((o instanceof NodesShutdownMetadata) == false) return false;
        NodesShutdownMetadata that = (NodesShutdownMetadata) o;
        return getPerNodeInfo().equals(that.getPerNodeInfo());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPerNodeInfo());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(NODES_FIELD.getPreferredName(), nodes);
        return builder;
    }

    /**
     * Handles diffing and appling diffs for {@link NodesShutdownMetadata} as necessary for the cluster state infrastructure.
     */
    public static class NodeShutdownMetadataDiff implements NamedDiff<Metadata.Custom> {

        private final Diff<Map<String, SingleNodeShutdownMetadata>> nodesDiff;

        NodeShutdownMetadataDiff(NodesShutdownMetadata before, NodesShutdownMetadata after) {
            this.nodesDiff = DiffableUtils.diff(before.nodes, after.nodes, DiffableUtils.getStringKeySerializer());
        }

        public NodeShutdownMetadataDiff(StreamInput in) throws IOException {
            this.nodesDiff = DiffableUtils.readJdkMapDiff(
                in,
                DiffableUtils.getStringKeySerializer(),
                SingleNodeShutdownMetadata::new,
                NodeShutdownMetadataDiff::readNodesDiffFrom
            );
        }

        @Override
        public Metadata.Custom apply(Metadata.Custom part) {
            TreeMap<String, SingleNodeShutdownMetadata> newNodes = new TreeMap<>(
                nodesDiff.apply(((NodesShutdownMetadata) part).getPerNodeInfo())
            );
            return new NodesShutdownMetadata(newNodes);
        }

        @Override
        public String getWriteableName() {
            return TYPE;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            nodesDiff.writeTo(out);
        }

        static Diff<SingleNodeShutdownMetadata> readNodesDiffFrom(StreamInput in) throws IOException {
            return AbstractDiffable.readDiffFrom(SingleNodeShutdownMetadata::new, in);
        }
    }

}
