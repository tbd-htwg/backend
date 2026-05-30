package com.tripplanning.seed;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.tripplanning.seed.assets.PlaceSeedCategory;
import com.tripplanning.seed.assets.PrefetchedPlace;

/**
 * Realistic trip, stop, user, and comment copy aligned with
 * {@code performance/seeding_example/seed_example_data.py}.
 */
final class PerfSeedText {

    private PerfSeedText() {}

    record TripTopic(
            String topic,
            String shortDescription,
            String[] longLeads,
            Set<PlaceSeedCategory> preferredStopCategories) {}

    private static final List<TripTopic> TRIP_TOPICS =
            List.of(
                    topic(
                            "City Break",
                            "Architecture walks and small plates after dark.",
                            "We keep plans loose enough for weather, yet firm on the one reservation that matters.",
                            "This route favors slow mornings, one long travel day, and evenings that end early.",
                            "I want a mix of city energy and quiet views without dragging heavy luggage everywhere."),
                    topic(
                            "Nature Escape",
                            "Lakeside trails, quiet peaks, and early sunrises.",
                            "A little hiking, a little culture, and enough downtime to read on a terrace somewhere.",
                            "Pack layers, pack patience, and keep phone maps offline for the mountain segments.",
                            "Most days end before dark so we can cook simple dinners or share a long table meal."),
                    topic(
                            "Culture Weekend",
                            "Museums, markets, and a bit of live music.",
                            "The goal is simple: good coffee, clear skies if lucky, and no rushed museum afternoons.",
                            "Book the small museum slot online; walk-ins wait longer than you would expect.",
                            "We balance solo wandering with one guided experience we would not DIY easily."),
                    topic(
                            "Food Pilgrimage",
                            "Local menus, wine bars, and one fancy reservation.",
                            "Trains first, short walks second, and one splurge meal where locals actually show up.",
                            "Try the set lunch menu before committing to the tasting menu on the last night.",
                            "I prefer small towns with one great bakery over megacities with endless ticket queues."),
                    topic(
                            "Slow Rail Trip",
                            "Window seats, transfers planned, no rushing.",
                            "We bookend the trip with easy arrivals so jet lag or late trains cannot ruin day one.",
                            "Reserve the window seat on the scenic leg; right side faces the lake.",
                            "Stretch after train rides; stiff legs make short stairs feel enormous."),
                    topic(
                            "Coastal Detour",
                            "Sea breeze, seafood, and short coastal hikes.",
                            "Rain plans exist: covered markets, thermal baths, or a long lazy brunch with friends.",
                            "Save energy for the sunset viewpoint; morning fog often burns off by ten.",
                            "We chase good light for photos but refuse to sprint between sights like a checklist."),
                    topic(
                            "Alpine Crossing",
                            "High passes, cable cars, and cozy evenings.",
                            "Start early once; the trailhead fills by mid-morning on sunny Saturdays.",
                            "Carry cash for the mountain hut; card readers fail more than you think.",
                            "We aim to return tired, happy, and with one story nobody back home will believe."),
                    topic(
                            "Urban Reset",
                            "Coffee shops, bookstores, and long city walks.",
                            "This itinerary leaves one blank half-day for whatever the hostel desk recommends.",
                            "Swap one museum for a long lunch if your feet need a softer day.",
                            "Wander cobblestones until your calves complain but your camera does not."),
                    topic(
                            "Festival Hop",
                            "One headline show plus time to wander.",
                            "One headline event booked; everything else stays flexible for pop-up gigs.",
                            "Evenings run late but mornings stay slow with coffee and a short walk.",
                            "Photograph ticket QR codes; paper fades when damp from mist or sweat."),
                    topic(
                            "Off-Season Quiet",
                            "Fewer crowds, shorter lines, longer chats.",
                            "Off-season means shorter queues and longer conversations with shop owners.",
                            "Pack a light rain shell even if the forecast looks perfect all week.",
                            "Download offline maps for the pass day; signal drops between stations."),
                    topic(
                            "Bike and Train",
                            "Two wheels in the day, sleeper or IC at night.",
                            "Two wheels by day, train by night — minimal luggage, maximum distance.",
                            "Ask for the regional card at the station kiosk; it pays off after three rides.",
                            "Ride a rental bike along the waterfront until my hands went numb from wind."),
                    topic(
                            "Lake Circuit",
                            "Ferries, swimming spots, and picnic lunches.",
                            "Ferries, swimming spots, and picnic lunches with no fixed timetable.",
                            "Walk the river path east first; crowds thin out after the second bridge.",
                            "Missed the first boat and laughed; the second one had better light anyway."),
                    topic(
                            "Castle Route",
                            "History stops and old-town photo walks.",
                            "History stops, old-town photo walks, and one rainy museum backup plan.",
                            "Sat on cathedral steps people-watching longer than any guidebook allows.",
                            "Found a quiet courtyard with a fountain and read until the bells rang."),
                    topic(
                            "Thermal Spa Week",
                            "Pools, saunas, and gentle hiking between soaks.",
                            "Pools, saunas, and gentle hiking between soaks — no sprinting allowed.",
                            "The spa week pacing feels realistic, not rushed at all.",
                            "Evenings end early so muscles recover before the next soak."),
                    topic(
                            "Photography Trek",
                            "Golden hour hikes and one rainy museum day.",
                            "We chase good light for photos but refuse to sprint between sights like a checklist.",
                            "Climbed one extra viewpoint; legs burned but the panorama paid it back.",
                            "Caught the city skyline at blue hour from a bridge locals use daily."),
                    topic(
                            "Hiking Weekend",
                            "Day hikes, trail snacks, and early nights.",
                            "Day hikes with trail snacks and early nights — legs over nightlife.",
                            "Hiked a shorter loop because clouds rolled in; still worth every step.",
                            "Seen the mountains wake up above fog from a ridge I almost skipped."),
                    topic(
                            "Museum Circuit",
                            "Block tickets, quiet galleries, and cafe breaks.",
                            "Block tickets for two museums and long cafe breaks between floors.",
                            "Metro to the museum district, then walked back along the river slowly.",
                            "Spent longer in the contemporary wing than planned — worth it."),
                    topic(
                            "Food Tour",
                            "Street food, markets, and one sit-down splurge.",
                            "Street food for lunch, markets for snacks, one sit-down splurge at night.",
                            "Street food near the station hit the spot before the sleeper train.",
                            "Skipped the famous cafe line and found a bakery locals queued at instead."),
                    topic(
                            "Sports Getaway",
                            "Active days, simple meals, and recovery walks.",
                            "Active days with simple meals and recovery walks built into the plan.",
                            "Swam in the lake even though the water was colder than I admitted aloud.",
                            "Rode a rental bike along the waterfront until my hands went numb from wind."),
                    topic(
                            "Cycling Day",
                            "Pedal routes, cafe stops, and train home.",
                            "Pedal routes with cafe stops and a train home before legs fully quit.",
                            "Took the funicular for views I had only seen on postcards before today.",
                            "Two wheels in the day, sleeper or IC at night."),
                    topic(
                            "Beach and Swim",
                            "Sand, salt air, and long lunches.",
                            "Sand, salt air, and long lunches with nowhere urgent to be.",
                            "Shared a cheese board and white wine while the lake turned pink at dusk.",
                            "Ended the day on a terrace, jacket on, watching trams glide below."),
                    topic(
                            "Night Market Crawl",
                            "Evening stalls, small bites, and neon streets.",
                            "Evening stalls, small bites, and neon streets until the last tram.",
                            "Listened to buskers near the market until rain sent everyone under awnings.",
                            "Late dinner at a tiny place the hotel desk warned was always full."),
                    topic(
                            "Wine Tasting",
                            "Cellar visits, vineyard views, and slow dinners.",
                            "Cellar visits, vineyard views, and slow dinners with local bottles.",
                            "Ate my weight in pastries after a long morning walk through old streets.",
                            "Try the set lunch menu before committing to the tasting menu on the last night."),
                    topic(
                            "Street Art Walk",
                            "Murals, back alleys, and independent cafes.",
                            "Murals, back alleys, and independent cafes away from the main drag.",
                            "Wandered cobblestones until my calves complained but my camera did not.",
                            "Found a quiet courtyard with a fountain and read until the bells rang."));

    private static final Map<String, String[]> REGIONAL_PARAGRAPHS =
            Map.of(
                    "alpine",
                    new String[] {
                        "Alpine legs need cable cars, early starts, and a backup indoor plan for foggy days.",
                        "Mountain passes look close on the map; build buffer time for weather and photo stops."
                    },
                    "mediterranean",
                    new String[] {
                        "Long lunches fit the rhythm here — siesta hours are real, not decorative.",
                        "Sea air and old-town lanes reward slow evenings more than packed morning checklists."
                    },
                    "nordic",
                    new String[] {
                        "Saunas, fjord views, and early sunsets shape the daily rhythm more than nightlife.",
                        "Layers matter more than fashion; daylight is short even when the forecast looks kind."
                    },
                    "asia",
                    new String[] {
                        "Temple mornings and night markets bookend the days — transit cards save queue time.",
                        "Ramen, markets, and one museum slot keep the pace lively without feeling rushed."
                    },
                    "islands",
                    new String[] {
                        "Ferries and island time mean one flexible day is not a luxury — it is logistics.",
                        "Wind and water set the schedule; keep one indoor backup for rough crossings."
                    });

    private static final Map<PlaceSeedCategory, String[]> STOP_PHRASES_BY_CATEGORY =
            Map.of(
                    PlaceSeedCategory.CAFE,
                    new String[] {
                        "Slow morning espresso and people-watching from a corner table.",
                        "Found a bakery locals queued at instead of the famous cafe line.",
                        "Stayed for a second coffee because the window seat had perfect light.",
                        "Tried the seasonal pastry and stayed longer than planned at {place}."
                    },
                    PlaceSeedCategory.RESTAURANT,
                    new String[] {
                        "Reservation for the tasting menu — every course felt local.",
                        "Late dinner at a tiny place the hotel desk warned was always full.",
                        "Set lunch menu hit the spot before an afternoon of walking.",
                        "Shared a long table meal at {place} and left before the kitchen closed."
                    },
                    PlaceSeedCategory.MUSEUM,
                    new String[] {
                        "Spent two hours in the permanent collection; the audio guide was worth it.",
                        "Booked a small museum slot online and skipped the walk-in queue.",
                        "Rain pushed us indoors; {place} turned out to be the best detour.",
                        "Contemporary wing took longer than planned — still the highlight of the day."
                    },
                    PlaceSeedCategory.TOURIST_ATTRACTION,
                    new String[] {
                        "Queued early; the view from the top made the wait fine.",
                        "Took the funicular for views I had only seen on postcards before today.",
                        "Classic stop at {place} — crowded but genuinely impressive up close.",
                        "Blue hour at {place} was worth the extra climb and cold fingers."
                    },
                    PlaceSeedCategory.PARK,
                    new String[] {
                        "Long walk under the trees before heading back to the hotel.",
                        "Hiked a shorter loop because clouds rolled in; still worth every step.",
                        "Picnic lunch in the shade at {place} with no schedule after.",
                        "Morning trail at {place} before the sun got too strong."
                    },
                    PlaceSeedCategory.VIEWPOINT,
                    new String[] {
                        "Golden hour from the ridge — best photos of the trip.",
                        "Climbed one extra viewpoint; legs burned but the panorama paid it back.",
                        "Seen the mountains wake up above fog from {place}.",
                        "Caught the skyline at blue hour from a spot locals use daily near {place}."
                    });

    private static final String[] USER_DESCRIPTION_SNIPPETS = {
        "Weekend trains and strong coffee.",
        "Slow travel and decent headphones.",
        "Hikes first, spreadsheets never on vacation.",
        "Street food over Michelin, usually.",
        "Photography walks and early nights.",
        "City museums plus one nature day.",
        "Hostels, host chats, hostel kitchens.",
        "Maps in pockets, phone mostly off.",
        "Lakes, ferries, and second breakfasts.",
        "Culture by day, jazz bars by night.",
    };

    private static final String[] COMMENT_PHRASES = {
        "Looks great! I would do this route too.",
        "Nice plan. The transport and stay choices look very practical.",
        "Saving this itinerary — the lake day is exactly what I needed.",
        "Love how you left one slow morning in the middle.",
        "That stop with the bakery line skip is genius.",
        "We did something similar last year; your train timing looks smarter.",
        "The spa week pacing feels realistic, not rushed at all.",
        "Would swap one museum for another hike, but this still works.",
        "Great mix of city and quiet — forwarding to a friend.",
        "Comments on the food notes section: spot on about the set lunch.",
        "This is the kind of trip I wish I had booked in April.",
        "Practical and still romantic — rare combo.",
    };

    private static TripTopic topic(
            String name,
            String shortDesc,
            String lead1,
            String lead2,
            String lead3) {
        return new TripTopic(name, shortDesc, new String[] {lead1, lead2, lead3}, EnumSet.noneOf(PlaceSeedCategory.class));
    }

    static TripTopic pickTripTopic(Random rng) {
        return TRIP_TOPICS.get(rng.nextInt(TRIP_TOPICS.size()));
    }

    /** ISO country codes where this trip theme reads naturally; empty means any destination. */
    static Set<String> preferredCountryCodes(TripTopic topic) {
        return switch (topic.topic()) {
            case "Alpine Crossing", "Hiking Weekend", "Photography Trek" ->
                    Set.of("CH", "AT", "FR", "IT", "DE", "SI", "LI", "NO");
            case "Coastal Detour", "Beach and Swim" ->
                    Set.of("GR", "ES", "PT", "IT", "HR", "MT", "CY", "AU", "TH", "MX", "US", "TR");
            case "Night Market Crawl" ->
                    Set.of("JP", "KR", "TH", "VN", "TW", "SG", "CN", "MY", "ID", "KH", "LA", "IN");
            case "Food Tour", "Food Pilgrimage" ->
                    Set.of("FR", "IT", "ES", "PT", "TH", "JP", "KR", "MX", "GR", "TR", "US", "VN", "SG");
            case "Wine Tasting" -> Set.of("FR", "IT", "ES", "PT", "DE", "AU", "US", "AR", "CL", "GR");
            case "Thermal Spa Week" -> Set.of("IS", "HU", "CZ", "CH", "FI", "JP", "AT", "DE");
            case "Lake Circuit" -> Set.of("CH", "IT", "AT", "NO", "SE", "FI", "CA", "DE");
            case "Slow Rail Trip", "Bike and Train" ->
                    Set.of("CH", "FR", "DE", "IT", "AT", "CZ", "GB", "ES", "JP", "NL", "BE");
            case "Nature Escape" -> Set.of("NO", "SE", "FI", "IS", "CH", "AT", "CA", "US", "NZ", "AU");
            case "Street Art Walk", "Urban Reset", "City Break" ->
                    Set.of("GB", "DE", "US", "FR", "ES", "NL", "BE", "AU", "JP", "KR", "MX", "AR");
            default -> Set.of();
        };
    }

    private static TripTopic findTopic(String name) {
        return TRIP_TOPICS.stream().filter(t -> t.topic().equals(name)).findFirst().orElse(TRIP_TOPICS.get(0));
    }

    static Set<PlaceSeedCategory> preferredStopCategories(TripTopic topic) {
        return switch (topic.topic()) {
            case "Food Pilgrimage", "Food Tour", "Wine Tasting", "Night Market Crawl" ->
                    EnumSet.of(PlaceSeedCategory.RESTAURANT, PlaceSeedCategory.CAFE);
            case "Museum Circuit", "Culture Weekend", "Castle Route" ->
                    EnumSet.of(PlaceSeedCategory.MUSEUM, PlaceSeedCategory.TOURIST_ATTRACTION);
            case "Hiking Weekend", "Nature Escape", "Photography Trek", "Alpine Crossing" ->
                    EnumSet.of(PlaceSeedCategory.PARK, PlaceSeedCategory.VIEWPOINT);
            case "Sports Getaway", "Cycling Day", "Beach and Swim" ->
                    EnumSet.of(PlaceSeedCategory.PARK, PlaceSeedCategory.TOURIST_ATTRACTION);
            default -> EnumSet.noneOf(PlaceSeedCategory.class);
        };
    }

    static String tripTitle(TripTopic topic) {
        return topic.topic();
    }

    static String tripLongDescription(Random rng, TripTopic topic, PrefetchedPlace dest, int tripCounter) {
        String city = displayCity(dest);
        String lead = topic.longLeads()[rng.nextInt(topic.longLeads().length)];
        StringBuilder body =
                new StringBuilder(
                        String.format(
                                "This %s is based in %s. %s",
                                topic.topic().toLowerCase(), city, lead));

        String regional = regionalParagraph(rng, dest.countryCode());
        if (regional != null && regionalParagraphFitsTopic(topic, dest.countryCode())) {
            body.append("\n\n").append(regional);
        }

        body.append("\n\n").append(topicActivityNote(rng, topic));

        if (rng.nextBoolean()) {
            body.append("\n\n- Pack a light rain shell even if the forecast looks perfect all week.");
            body.append("\n- Download offline maps for scenic legs; signal drops between stops.");
            body.append("\n- Leave one half-day unplanned for whatever locals recommend in ").append(city).append(".");
        }

        body.append("\n\nSeed dataset trip #").append(tripCounter);
        return body.toString();
    }

    private static String displayCity(PrefetchedPlace dest) {
        if (dest.cityName() != null && !dest.cityName().isBlank()) {
            return dest.cityName();
        }
        return dest.placeName() != null && !dest.placeName().isBlank() ? dest.placeName() : "the area";
    }

    private static boolean regionalParagraphFitsTopic(TripTopic topic, String countryCode) {
        String region = regionalKey(countryCode);
        return switch (topic.topic()) {
            case "Alpine Crossing", "Hiking Weekend", "Photography Trek", "Nature Escape" ->
                    "alpine".equals(region) || "nordic".equals(region);
            case "Coastal Detour", "Beach and Swim", "Food Pilgrimage", "Wine Tasting" ->
                    "mediterranean".equals(region) || "islands".equals(region);
            case "Night Market Crawl", "Food Tour", "Street Art Walk" -> "asia".equals(region);
            case "Lake Circuit", "Thermal Spa Week" ->
                    "nordic".equals(region) || "alpine".equals(region) || "islands".equals(region);
            default -> !region.isBlank();
        };
    }

    private static String topicActivityNote(Random rng, TripTopic topic) {
        String[] notes =
                switch (topic.topic()) {
                    case "Food Pilgrimage", "Food Tour", "Wine Tasting", "Night Market Crawl" ->
                            new String[] {
                                "Stops lean toward cafes, markets, and sit-down meals rather than museums.",
                                "Most days revolve around one long meal and a short walk between bites."
                            };
                    case "Museum Circuit", "Culture Weekend", "Castle Route" ->
                            new String[] {
                                "Museums and landmarks anchor the days; cafes fill the gaps.",
                                "Gallery time is blocked in the morning when crowds are thinnest."
                            };
                    case "Hiking Weekend", "Alpine Crossing", "Nature Escape", "Photography Trek" ->
                            new String[] {
                                "Trailheads and viewpoints come first; town stops are for food and sleep.",
                                "Weather decides the order — indoor backups stay on the list."
                            };
                    case "Coastal Detour", "Beach and Swim", "Lake Circuit" ->
                            new String[] {
                                "Waterfront walks and ferry hops shape the rhythm more than transit hubs.",
                                "Swim gear and a wind layer live in the day pack all week."
                            };
                    case "Slow Rail Trip", "Bike and Train", "Cycling Day" ->
                            new String[] {
                                "Train and bike legs are timed so luggage only moves once or twice.",
                                "Window seats and short platform transfers beat rushing between cities."
                            };
                    default ->
                            new String[] {
                                "Stops mix well-known sights with one neighborhood wander per day.",
                                "The pace stays human — one anchor activity, then room to improvise."
                            };
                };
        return notes[rng.nextInt(notes.length)];
    }

    static String stopDescription(Random rng, PlaceSeedCategory category, String placeName) {
        String[] pool =
                STOP_PHRASES_BY_CATEGORY.getOrDefault(
                        category, STOP_PHRASES_BY_CATEGORY.get(PlaceSeedCategory.TOURIST_ATTRACTION));
        String phrase = pool[rng.nextInt(pool.length)];
        String safeName = placeName != null && !placeName.isBlank() ? placeName : "this spot";
        return phrase.replace("{place}", safeName);
    }

    static String userDescription(Random rng, long userId) {
        if (userId == 1L) {
            return "Smoke-test account for perf seed.";
        }
        return USER_DESCRIPTION_SNIPPETS[(int) ((userId - 2) % USER_DESCRIPTION_SNIPPETS.length)];
    }

    static String comment(Random rng, long tripId, long authorId) {
        int idx = Math.floorMod(tripId * 31 + authorId * 17, COMMENT_PHRASES.length);
        if (rng.nextBoolean()) {
            idx = rng.nextInt(COMMENT_PHRASES.length);
        }
        return COMMENT_PHRASES[idx];
    }

    private static String regionalParagraph(Random rng, String countryCode) {
        String key = regionalKey(countryCode);
        String[] paragraphs = REGIONAL_PARAGRAPHS.get(key);
        if (paragraphs == null) {
            return null;
        }
        return paragraphs[rng.nextInt(paragraphs.length)];
    }

    private static String regionalKey(String countryCode) {
        if (countryCode == null) {
            return "";
        }
        String cc = countryCode.toUpperCase();
        if (Set.of("CH", "AT", "FR", "IT", "DE", "SI", "LI").contains(cc)) {
            return "alpine";
        }
        if (Set.of("GR", "ES", "PT", "IT", "HR", "MT", "CY").contains(cc)) {
            return "mediterranean";
        }
        if (Set.of("NO", "SE", "FI", "IS", "DK").contains(cc)) {
            return "nordic";
        }
        if (Set.of("JP", "KR", "TH", "VN", "TW", "SG", "CN", "IN", "ID", "MY", "KH", "LA").contains(cc)) {
            return "asia";
        }
        if (Set.of("MV", "FJ", "NC", "PF", "SC", "MU", "GR").contains(cc)) {
            return "islands";
        }
        return "";
    }
}
