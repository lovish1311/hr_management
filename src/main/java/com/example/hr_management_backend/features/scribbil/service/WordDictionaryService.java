package com.example.hr_management_backend.features.scribbil.service;

import com.example.hr_management_backend.features.scribbil.dto.WordOptionDto;
import com.example.hr_management_backend.features.scribbil.model.DrawGuessWord;
import com.example.hr_management_backend.features.scribbil.repository.DrawGuessWordRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordDictionaryService {

    private final DrawGuessWordRepository wordRepository;

    private static final List<WordSeed> DEFAULT_WORDS = List.of(
            // TECH
            new WordSeed("Computer", "TECH", "EASY", "Electronic device on your desk"),
            new WordSeed("Keyboard", "TECH", "EASY", "Used for typing"),
            new WordSeed("Mouse", "TECH", "EASY", "Clicking device with a cursor"),
            new WordSeed("Monitor", "TECH", "EASY", "Screen display"),
            new WordSeed("Laptop", "TECH", "EASY", "Portable personal computer"),
            new WordSeed("Server", "TECH", "MEDIUM", "Hosts backend services in a rack"),
            new WordSeed("Database", "TECH", "MEDIUM", "Stores organized tables and records"),
            new WordSeed("Cloud", "TECH", "EASY", "Remote servers across the internet"),
            new WordSeed("Router", "TECH", "MEDIUM", "Distributes Wi-Fi and network packets"),
            new WordSeed("Headphones", "TECH", "EASY", "Worn on ears for audio"),
            new WordSeed("Smartwatch", "TECH", "MEDIUM", "Wearable clock with digital apps"),
            new WordSeed("Firewall", "TECH", "HARD", "Security barrier blocking unauthorized traffic"),
            new WordSeed("Algorithm", "TECH", "HARD", "Step-by-step computational logic"),
            new WordSeed("Bug", "TECH", "EASY", "Software defect or insect"),
            new WordSeed("Robot", "TECH", "EASY", "Automated programmable machine"),

            // HR & OFFICE
            new WordSeed("Whiteboard", "HR_OFFICE", "EASY", "Used in meeting rooms for brainstorming"),
            new WordSeed("Coffee", "HR_OFFICE", "EASY", "Popular morning beverage in pantry"),
            new WordSeed("Chair", "HR_OFFICE", "EASY", "Ergonomic furniture you sit on"),
            new WordSeed("Desk", "HR_OFFICE", "EASY", "Work table in the cubicle"),
            new WordSeed("Projector", "HR_OFFICE", "MEDIUM", "Beams presentations onto a wall"),
            new WordSeed("Water Bottle", "HR_OFFICE", "EASY", "Keeps you hydrated at work"),
            new WordSeed("ID Card", "HR_OFFICE", "EASY", "Badge used to scan at turnstiles"),
            new WordSeed("Backpack", "HR_OFFICE", "EASY", "Carries your laptop to office"),
            new WordSeed("Calendar", "HR_OFFICE", "EASY", "Shows dates, meetings and holidays"),
            new WordSeed("Clock", "HR_OFFICE", "EASY", "Tells login and logout time"),
            new WordSeed("Notepad", "HR_OFFICE", "EASY", "Paper pad for quick meeting notes"),
            new WordSeed("Stapler", "HR_OFFICE", "MEDIUM", "Fastens sheets of paper together"),
            new WordSeed("Printer", "HR_OFFICE", "MEDIUM", "Outputs documents on paper"),

            // ANIMALS & NATURE
            new WordSeed("Elephant", "ANIMALS", "EASY", "Large mammal with a trunk"),
            new WordSeed("Giraffe", "ANIMALS", "EASY", "Tall animal with a long neck"),
            new WordSeed("Penguin", "ANIMALS", "EASY", "Flightless bird in cold ice"),
            new WordSeed("Tiger", "ANIMALS", "EASY", "Striped big cat"),
            new WordSeed("Dolphin", "ANIMALS", "MEDIUM", "Intelligent marine creature"),
            new WordSeed("Kangaroo", "ANIMALS", "MEDIUM", "Hops and has a pouch"),
            new WordSeed("Eagle", "ANIMALS", "MEDIUM", "Sharp-eyed bird of prey"),
            new WordSeed("Butterfly", "ANIMALS", "EASY", "Colorful winged insect"),
            new WordSeed("Volcano", "NATURE", "MEDIUM", "Mountain erupting lava"),
            new WordSeed("Rainbow", "NATURE", "EASY", "Seven colors in the sky after rain"),
            new WordSeed("Waterfall", "NATURE", "MEDIUM", "Water cascading down a cliff"),
            new WordSeed("Sunflower", "NATURE", "EASY", "Tall yellow flower facing the sun"),

            // OBJECTS & FOOD
            new WordSeed("Pizza", "FOOD", "EASY", "Cheesy Italian sliced pie"),
            new WordSeed("Burger", "FOOD", "EASY", "Bun with patty and lettuce"),
            new WordSeed("Ice Cream", "FOOD", "EASY", "Cold sweet dessert on a cone"),
            new WordSeed("Bicycle", "OBJECTS", "EASY", "Two-wheeled pedal vehicle"),
            new WordSeed("Airplane", "OBJECTS", "EASY", "Flies passengers in the sky"),
            new WordSeed("Guitar", "OBJECTS", "EASY", "Six-string musical instrument"),
            new WordSeed("Telescope", "OBJECTS", "MEDIUM", "Looks at distant stars and planets"),
            new WordSeed("Umbrella", "OBJECTS", "EASY", "Protects against rain and sun"),
            new WordSeed("Sunglasses", "OBJECTS", "EASY", "Dark eyewear for bright sun"),
            new WordSeed("Rocket", "OBJECTS", "MEDIUM", "Spacecraft traveling to orbit"),
            new WordSeed("Submarine", "OBJECTS", "HARD", "Underwater vessel"),
            new WordSeed("Campfire", "OBJECTS", "MEDIUM", "Outdoor fire for camping")
    );

    @PostConstruct
    public void seedInitialWords() {
        try {
            if (wordRepository.count() == 0) {
                List<DrawGuessWord> wordsToSave = DEFAULT_WORDS.stream()
                        .map(s -> DrawGuessWord.builder()
                                .word(s.word)
                                .category(s.category)
                                .difficulty(s.difficulty)
                                .hintText(s.hint)
                                .isCustom(false)
                                .build())
                        .toList();
                wordRepository.saveAll(wordsToSave);
                log.info("Successfully seeded {} default Draw & Guess words into repository.", wordsToSave.size());
            }
        } catch (Exception e) {
            log.warn("Could not seed default words: {}", e.getMessage());
        }
    }

    @org.springframework.beans.factory.annotation.Value("${gemini.api.key:}")
    private String geminiApiKey;

    @org.springframework.beans.factory.annotation.Value("${gemini.api.model:gemini-3.1-flash-lite}")
    private String geminiModel;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public List<String> generateWordsForGame(String category, int count) {
        int targetCount = Math.max(count, 45);
        List<String> aiWords = fetchWordsFromGemini(category, targetCount);
        if (aiWords != null && aiWords.size() >= 15) {
            log.info("Successfully generated {} Draw & Guess words via Gemini AI for category '{}'", aiWords.size(), category);
            return aiWords;
        }

        log.warn("Gemini word generation returned insufficient words ({}), falling back to built-in dictionary",
                aiWords != null ? aiWords.size() : 0);
        return getFallbackWords(category, targetCount);
    }

    private List<String> fetchWordsFromGemini(String category, int count) {
        String cat = (category == null || category.isBlank()) ? "GENERAL" : category.trim();
        String prompt = String.format(
                "Generate a JSON array of %d unique, single-word or simple two-word common nouns suitable for a Draw & Guess (skribbl) game under the category '%s'. " +
                "Only common objects, animals, places, or everyday concepts that people can easily draw and guess. " +
                "Return ONLY a valid JSON array of strings, for example: [\"Apple\", \"Giraffe\", \"Waterfall\"]. Do NOT include markdown blocks, explanations, or quotes outside the array.",
                count, cat
        );

        String[] modelCandidates = {
            (geminiModel != null && !geminiModel.isBlank()) ? geminiModel : "gemini-3.1-flash-lite",
            "gemini-3.8-flash",
            "gemini-flash-latest"
        };

        for (String model : modelCandidates) {
            try {
                String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + geminiApiKey;
                java.net.URL url = new java.net.URL(endpoint);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3500);

                Map<String, Object> reqBody = Map.of(
                        "contents", List.of(
                                Map.of("parts", List.of(
                                        Map.of("text", prompt)
                                ))
                        )
                );

                byte[] bodyBytes = objectMapper.writeValueAsBytes(reqBody);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }

                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) {
                    try (java.io.InputStream is = conn.getInputStream()) {
                        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(is);
                        com.fasterxml.jackson.databind.JsonNode textNode = rootNode.path("candidates").path(0)
                                .path("content").path("parts").path(0).path("text");
                        if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                            String rawText = textNode.asText().trim();
                            // Clean potential markdown blocks
                            if (rawText.startsWith("```")) {
                                int firstLine = rawText.indexOf('\n');
                                int lastBlock = rawText.lastIndexOf("```");
                                if (firstLine != -1 && lastBlock > firstLine) {
                                    rawText = rawText.substring(firstLine + 1, lastBlock).trim();
                                }
                            }

                            List<String> parsedWords = objectMapper.readValue(
                                    rawText,
                                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                            );

                            List<String> cleaned = parsedWords.stream()
                                    .map(String::trim)
                                    .filter(w -> !w.isBlank() && w.length() >= 2 && w.length() <= 20)
                                    .distinct()
                                    .toList();

                            if (!cleaned.isEmpty()) {
                                return new ArrayList<>(cleaned);
                            }
                        }
                    }
                } else {
                    log.warn("Gemini model {} returned HTTP status {}", model, statusCode);
                }
            } catch (Exception e) {
                log.warn("Failed fetching words from Gemini model {}: {}", model, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    public List<String> getFallbackWords(String category, int count) {
        List<DrawGuessWord> dbWords;
        if (category == null || "ALL".equalsIgnoreCase(category) || "GENERAL".equalsIgnoreCase(category)) {
            dbWords = wordRepository.findAll();
        } else {
            dbWords = wordRepository.findByCategory(category.toUpperCase());
        }

        Set<String> words = new LinkedHashSet<>();
        if (dbWords != null) {
            for (DrawGuessWord w : dbWords) {
                words.add(w.getWord().trim());
            }
        }

        for (WordSeed s : DEFAULT_WORDS) {
            if (category == null || "ALL".equalsIgnoreCase(category) || "GENERAL".equalsIgnoreCase(category)
                    || s.category.equalsIgnoreCase(category)) {
                words.add(s.word.trim());
            }
        }

        List<String> list = new ArrayList<>(words);
        Collections.shuffle(list);
        if (list.size() > count) {
            return new ArrayList<>(list.subList(0, count));
        }
        return list;
    }

    public List<WordOptionDto> getRandomWordOptions(String category, int count) {
        List<DrawGuessWord> dbWords;
        if (category == null || "ALL".equalsIgnoreCase(category) || "GENERAL".equalsIgnoreCase(category)) {
            dbWords = wordRepository.findRandomWordsByCategory(null, count);
        } else {
            dbWords = wordRepository.findRandomWordsByCategory(category.toUpperCase(), count);
        }

        if (dbWords.size() < count) {
            // Fallback to in-memory seeds
            List<WordSeed> pool = new ArrayList<>(DEFAULT_WORDS);
            Collections.shuffle(pool);
            return pool.stream()
                    .limit(count)
                    .map(s -> WordOptionDto.builder()
                            .word(s.word)
                            .category(s.category)
                            .difficulty(s.difficulty)
                            .hint(s.hint)
                            .build())
                    .toList();
        }

        return dbWords.stream()
                .map(w -> WordOptionDto.builder()
                        .word(w.getWord())
                        .category(w.getCategory())
                        .difficulty(w.getDifficulty())
                        .hint(w.getHintText())
                        .build())
                .toList();
    }

    public List<String> getAvailableCategories() {
        Set<String> categories = new LinkedHashSet<>(List.of("GENERAL", "TECH", "HR_OFFICE", "ANIMALS", "NATURE", "FOOD", "OBJECTS"));
        try {
            categories.addAll(wordRepository.findDistinctCategories());
        } catch (Exception ignored) {}
        return new ArrayList<>(categories);
    }

    private record WordSeed(String word, String category, String difficulty, String hint) {}
}

