package com.jinjin.memory;

import com.jinjin.memory.application.DefaultMemoryService;
import com.jinjin.memory.application.MemoryIndexer;
import com.jinjin.memory.application.MemoryTimelineIndexer;
import com.jinjin.memory.application.TopicKeyResolver;
import com.jinjin.memory.application.MemoryCommandService;
import com.jinjin.memory.application.MemoryQueryService;
import com.jinjin.memory.cli.TerminalLoop;
import com.jinjin.memory.cli.TerminalRenderer;
import com.jinjin.memory.cli.command.DefaultCommandDispatcher;
import com.jinjin.memory.cli.command.DefaultCommandParser;
import com.jinjin.memory.cli.command.DefaultCommandValidator;
import com.jinjin.memory.cli.command.ParsedCommand;
import com.jinjin.memory.cli.command.ValidationResult;
import com.jinjin.memory.cli.command.executor.AddMemoryCommandExecutor;
import com.jinjin.memory.cli.command.executor.DeleteMemoryCommandExecutor;
import com.jinjin.memory.cli.command.executor.HelpCommandExecutor;
import com.jinjin.memory.cli.command.executor.ListMemoryCommandExecutor;
import com.jinjin.memory.cli.command.executor.SearchMemoryCommandExecutor;
import com.jinjin.memory.cli.command.executor.SeedBasicKnowledgeCommandExecutor;
import com.jinjin.memory.cli.command.executor.ShowMemoryCommandExecutor;
import com.jinjin.memory.cli.command.executor.EmbedCommandExecutor;
import com.jinjin.memory.cli.command.executor.ReindexGraphCommandExecutor;
import com.jinjin.memory.cli.command.executor.StatsCommandExecutor;
import com.jinjin.memory.cli.command.executor.RecallMemoryCommandExecutor;
import com.jinjin.memory.cli.command.executor.RebuildAssociativeCommandExecutor;
import com.jinjin.memory.cli.command.executor.TimelineCommandExecutor;
import com.jinjin.memory.infrastructure.associative.AssociativeMemoryEngine;
import com.jinjin.memory.infrastructure.associative.SparseUnitEncoder;
import com.jinjin.memory.infrastructure.persistence.JsonlAssociativeMemoryRepository;
import com.jinjin.memory.infrastructure.persistence.JsonlMemoryGraphRepository;
import com.jinjin.memory.infrastructure.persistence.JsonlMemoryRepository;
import com.jinjin.memory.infrastructure.persistence.JsonlMemoryTimelineRepository;
import com.jinjin.memory.infrastructure.search.HierarchicalRecallEngine;
import com.jinjin.memory.infrastructure.search.SearchEngine;
import com.jinjin.memory.infrastructure.semantic.SemanticEnricher;
import com.jinjin.memory.infrastructure.semantic.SemanticServiceClient;
import com.jinjin.memory.infrastructure.temporal.EventTemporalParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final Path DATA_FILE = Path.of("data", "memories.jsonl");
    private static final Path GRAPH_DIR = Path.of("data", "graph");
    private static final Path KEYWORD_INDEX_FILE = Path.of("data", "keyword-index.jsonl");
    private static final Path ASSOCIATIVE_DIR = Path.of("data", "associative");
    private static final Path TIMELINE_DIR = Path.of("data", "timeline");

    public static void main(String[] args) throws IOException {
        AppComponents components = buildComponents();

        if (args.length > 0) {
            runOnce(args, components);
            return;
        }

        new TerminalLoop(
                components.parser(),
                components.validator(),
                components.dispatcher(),
                components.renderer()
        ).run();
    }

    private static void runOnce(String[] args, AppComponents components) {
        ParsedCommand command = components.parser().parse(String.join(" ", args));
        ValidationResult validation = components.validator().validate(command);
        if (!validation.valid()) {
            components.renderer().render(validation.toCommandResult());
            return;
        }
        components.renderer().render(components.dispatcher().dispatch(command));
    }

    private static AppComponents buildComponents() {
        JsonlMemoryRepository repository = new JsonlMemoryRepository(DATA_FILE);
        JsonlMemoryGraphRepository graphRepository = new JsonlMemoryGraphRepository(GRAPH_DIR);
        MemoryIndexer indexer = new MemoryIndexer(graphRepository);
        MemoryTimelineIndexer timelineIndexer = new MemoryTimelineIndexer(
                new JsonlMemoryTimelineRepository(TIMELINE_DIR)
        );
        Optional<SemanticServiceClient> semanticClient = SemanticServiceClient.fromEnvironment();
        semanticClient.ifPresentOrElse(
                client -> {
                    if (client.ping()) {
                        LOGGER.info("语义侧车已连接");
                    } else {
                        LOGGER.warning("语义侧车未响应，将回退到本地规则标注");
                    }
                },
                () -> LOGGER.info("语义侧车已禁用（JINJIN_SEMANTIC_URL=off）")
        );
        SemanticEnricher enricher = new SemanticEnricher(semanticClient);
        HierarchicalRecallEngine recallEngine = new HierarchicalRecallEngine(graphRepository);
        AssociativeMemoryEngine associativeMemory = new AssociativeMemoryEngine(
                new JsonlAssociativeMemoryRepository(ASSOCIATIVE_DIR),
                new SparseUnitEncoder()
        );
        DefaultMemoryService memoryService;
        try {
            memoryService = new DefaultMemoryService(
                    repository,
                    graphRepository,
                    indexer,
                    timelineIndexer,
                    new EventTemporalParser(),
                    new TopicKeyResolver(),
                    enricher,
                    semanticClient,
                    new SearchEngine(),
                    recallEngine,
                    associativeMemory,
                    KEYWORD_INDEX_FILE
            );
        } catch (IOException e) {
            throw new IllegalStateException("无法加载关键词索引", e);
        }
        MemoryCommandService commandService = memoryService;
        MemoryQueryService queryService = memoryService;

        DefaultCommandParser parser = new DefaultCommandParser();
        DefaultCommandValidator validator = new DefaultCommandValidator();
        TerminalRenderer renderer = new TerminalRenderer();
        DefaultCommandDispatcher dispatcher = new DefaultCommandDispatcher(List.of(
                new AddMemoryCommandExecutor(commandService),
                new SearchMemoryCommandExecutor(queryService),
                new RecallMemoryCommandExecutor(queryService),
                new TimelineCommandExecutor(queryService),
                new ListMemoryCommandExecutor(queryService),
                new ShowMemoryCommandExecutor(queryService),
                new DeleteMemoryCommandExecutor(commandService),
                new SeedBasicKnowledgeCommandExecutor(commandService),
                new ReindexGraphCommandExecutor(commandService),
                new RebuildAssociativeCommandExecutor(commandService),
                new EmbedCommandExecutor(commandService),
                new StatsCommandExecutor(queryService),
                new HelpCommandExecutor()
        ));

        return new AppComponents(parser, validator, dispatcher, renderer);
    }
}
