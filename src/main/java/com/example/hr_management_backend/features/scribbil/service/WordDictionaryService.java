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
