package com.tripplanning.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Realistic trip, stop, user, and comment copy aligned with
 * {@code performance/seeding_example/seed_example_data.py}.
 */
final class PerfSeedText {

    private PerfSeedText() {}

    record TripTopic(String topic, String shortDescription) {}

    private static final List<TripTopic> TRIP_TOPICS =
            List.of(
                    new TripTopic("City Break", "Architecture walks and small plates after dark."),
                    new TripTopic("Nature Escape", "Lakeside trails, quiet peaks, and early sunrises."),
                    new TripTopic("Culture Weekend", "Museums, markets, and a bit of live music."),
                    new TripTopic("Food Pilgrimage", "Local menus, wine bars, and one fancy reservation."),
                    new TripTopic("Slow Rail Trip", "Window seats, transfers planned, no rushing."),
                    new TripTopic("Coastal Detour", "Sea breeze, seafood, and short coastal hikes."),
                    new TripTopic("Alpine Crossing", "High passes, cable cars, and cozy evenings."),
                    new TripTopic("Urban Reset", "Coffee shops, bookstores, and long city walks."),
                    new TripTopic("Festival Hop", "One headline show plus time to wander."),
                    new TripTopic("Off-Season Quiet", "Fewer crowds, shorter lines, longer chats."),
                    new TripTopic("Bike and Train", "Two wheels in the day, sleeper or IC at night."),
                    new TripTopic("Lake Circuit", "Ferries, swimming spots, and picnic lunches."),
                    new TripTopic("Castle Route", "History stops and old-town photo walks."),
                    new TripTopic("Thermal Spa Week", "Pools, saunas, and gentle hiking between soaks."),
                    new TripTopic("Photography Trek", "Golden hour hikes and one rainy museum day."));

    private static final String[] LEAD_PARAGRAPHS = {
        "We keep plans loose enough for weather, yet firm on the one reservation that matters.",
        "This route favors slow mornings, one long travel day, and evenings that end early.",
        "I want a mix of city energy and quiet views without dragging heavy luggage everywhere.",
        "Trains first, short walks second, and one splurge meal where locals actually show up.",
        "The goal is simple: good coffee, clear skies if lucky, and no rushed museum afternoons.",
        "We bookend the trip with easy arrivals so jet lag or late trains cannot ruin day one.",
        "A little hiking, a little culture, and enough downtime to read on a terrace somewhere.",
        "Most days end before dark so we can cook simple dinners or share a long table meal.",
        "I prefer small towns with one great bakery over megacities with endless ticket queues.",
        "This itinerary leaves one blank half-day for whatever the hostel desk recommends.",
        "We chase good light for photos but refuse to sprint between sights like a checklist.",
        "Rain plans exist: covered markets, thermal baths, or a long lazy brunch with friends.",
        "The trip balances solo wandering with one guided experience we would not DIY easily.",
        "Pack layers, pack patience, and keep phone maps offline for the mountain segments.",
        "We aim to return tired, happy, and with one story nobody back home will believe.",
    };

    private static final String[] LONG_DESC_SECTION_TITLES =
            {"Highlights", "Food notes", "Day two", "Evening plans", "Local tips"};

    private static final String[] LONG_DESC_SECTION_BODIES = {
        "Save energy for the sunset viewpoint; morning fog often burns off by ten.",
        "Try the set lunch menu before committing to the tasting menu on the last night.",
        "Walk the river path east first; crowds thin out after the second bridge.",
        "Ask for the regional card at the station kiosk; it pays off after three rides.",
        "Book the small museum slot online; walk-ins wait longer than you would expect.",
    };

    private static final String[] BULLET_SNIPPETS = {
        "Pack a light rain shell even if the forecast looks perfect all week.",
        "Download offline maps for the pass day; signal drops between stations.",
        "Reserve the window seat on the scenic leg; right side faces the lake.",
        "Carry cash for the mountain hut; card readers fail more than you think.",
        "Start early once; the trailhead fills by mid-morning on sunny Saturdays.",
        "Swap one museum for a long lunch if your feet need a softer day.",
        "Photograph ticket QR codes; paper fades when damp from mist or sweat.",
        "Stretch after train rides; stiff legs make short stairs feel enormous.",
    };

    private static final String[] STOP_PHRASES = {
        "Tried the local set lunch and stayed for an extra espresso in the sun.",
        "Late dinner at a tiny place the hotel desk warned was always full.",
        "Ate my weight in pastries after a long morning walk through old streets.",
        "Shared a cheese board and white wine while the lake turned pink at dusk.",
        "Street food near the station hit the spot before the sleeper train.",
        "Seen the mountains wake up above fog from a ridge I almost skipped.",
        "Hiked a shorter loop because clouds rolled in; still worth every step.",
        "Caught the city skyline at blue hour from a bridge locals use daily.",
        "Wandered cobblestones until my calves complained but my camera did not.",
        "Metro to the museum district, then walked back along the river slowly.",
        "Sat on cathedral steps people-watching longer than any guidebook allows.",
        "Found a quiet courtyard with a fountain and read until the bells rang.",
        "Took the funicular for views I had only seen on postcards before today.",
        "Swam in the lake even though the water was colder than I admitted aloud.",
        "Missed the first boat and laughed; the second one had better light anyway.",
        "Listened to buskers near the market until rain sent everyone under awnings.",
        "Climbed one extra viewpoint; legs burned but the panorama paid it back.",
        "Skipped the famous cafe line and found a bakery locals queued at instead.",
        "Rode a rental bike along the waterfront until my hands went numb from wind.",
        "Ended the day on a terrace, jacket on, watching trams glide below.",
    };

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

    static TripTopic pickTripTopic(Random rng) {
        return TRIP_TOPICS.get(rng.nextInt(TRIP_TOPICS.size()));
    }

    static String tripTitle(TripTopic topic, int tripCounter) {
        return topic.topic() + " #" + tripCounter;
    }

    static String tripLongDescription(Random rng) {
        String lead = LEAD_PARAGRAPHS[rng.nextInt(LEAD_PARAGRAPHS.length)];
        int kind = rng.nextInt(3);
        if (kind == 0) {
            return lead;
        }
        if (kind == 1) {
            List<String> pool = new ArrayList<>(List.of(BULLET_SNIPPETS));
            Collections.shuffle(pool, rng);
            return lead + "\n\n- " + pool.get(0) + "\n- " + pool.get(1) + "\n- " + pool.get(2);
        }
        String title = LONG_DESC_SECTION_TITLES[rng.nextInt(LONG_DESC_SECTION_TITLES.length)];
        String body = LONG_DESC_SECTION_BODIES[rng.nextInt(LONG_DESC_SECTION_BODIES.length)];
        return lead + "\n\n## " + title + "\n\n" + body;
    }

    static List<String> stopDescriptions(Random rng, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<String> pool = new ArrayList<>(List.of(STOP_PHRASES));
        Collections.shuffle(pool, rng);
        return pool.subList(0, Math.min(count, pool.size()));
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
}
