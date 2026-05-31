#!/usr/bin/env python3
"""Fetch Google Places into tripplanning-seed-job/src/main/resources/seed/google-places.json.

Live mode uses:
  1. ``GET {api-base}/api/v2/external/details/search?q=…`` (public, via ingress)
  2. ``GET {internal-base}/internal/location-pack?placeId=…&fresh=true`` with
     ``X-Internal-Secret`` (external-info-service — **not** ``/stop-details``, which is weather-only)

Prerequisites:
  - Ingress / API on ``--api-base`` (e.g. ``./scripts/local-dev.sh port-forward`` → :8080)
  - external-info reachable on ``--internal-base`` (e.g. ``kubectl port-forward -n tripplanning
    svc/external-info-service 8082:8082``)
  - ``GOOGLE_MAPS_API_KEY`` on external-info-service
  - ``TRIPPLANNING_INTERNAL_SECRET`` matching the cluster (default: dev-internal-service-secret)

  python3 backend/scripts/fetch-google-places.py --test --api-base http://localhost:8080
  python3 backend/scripts/fetch-google-places.py --api-base http://localhost:8080
  python3 backend/scripts/fetch-google-places.py --synthetic
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
BACKEND_DIR = SCRIPT_DIR.parent
OUT_PATH = BACKEND_DIR / "tripplanning-seed-job" / "src" / "main" / "resources" / "seed" / "google-places.json"

POI_CITY_ANCHORS = [
    "Paris France",
    "London United Kingdom",
    "Berlin Germany",
    "Rome Italy",
    "Barcelona Spain",
    "Amsterdam Netherlands",
    "Vienna Austria",
    "Prague Czech Republic",
    "Zurich Switzerland",
    "Tokyo Japan",
    "Kyoto Japan",
    "Seoul South Korea",
    "Bangkok Thailand",
    "Singapore",
    "Sydney Australia",
    "New York USA",
    "San Francisco USA",
    "Los Angeles USA",
    "Chicago USA",
    "Toronto Canada",
    "Mexico City Mexico",
    "Buenos Aires Argentina",
    "Rio de Janeiro Brazil",
    "Cape Town South Africa",
    "Marrakech Morocco",
    "Dubai UAE",
    "Istanbul Turkey",
    "Athens Greece",
    "Lisbon Portugal",
    "Copenhagen Denmark",
    "Stockholm Sweden",
    "Oslo Norway",
    "Helsinki Finland",
    "Dublin Ireland",
    "Edinburgh United Kingdom",
    "Brussels Belgium",
    "Munich Germany",
    "Milan Italy",
    "Venice Italy",
    "Madrid Spain",
    "Hong Kong",
    "Taipei Taiwan",
    "Hanoi Vietnam",
    "Ho Chi Minh City Vietnam",
    "Kuala Lumpur Malaysia",
    "Jakarta Indonesia",
    "Manila Philippines",
    "Auckland New Zealand",
    "Honolulu Hawaii",
    "Boston USA",
    "Seattle USA",
    "Montreal Canada",
    "Lima Peru",
    "Santiago Chile",
    "Reykjavik Iceland",
    "Budapest Hungary",
    "Warsaw Poland",
    "Krakow Poland",
    "Dubrovnik Croatia",
    "Florence Italy",
    "Nice France",
    "Chamonix France",
    "Interlaken Switzerland",
    "Nara Japan",
    "Siem Reap Cambodia",
    "Chiang Mai Thailand",
]

# Destinations and lodging cities from performance/seeding_example/seed_example_data.py (subset + extras).
SEARCH_QUERIES = [
    "Zurich Switzerland",
    "Geneva Switzerland",
    "Bern Switzerland",
    "Basel Switzerland",
    "Interlaken Switzerland",
    "Zermatt Switzerland",
    "Lausanne Switzerland",
    "Lugano Switzerland",
    "Paris France",
    "Lyon France",
    "Marseille France",
    "London United Kingdom",
    "Edinburgh United Kingdom",
    "Berlin Germany",
    "Munich Germany",
    "Hamburg Germany",
    "Vienna Austria",
    "Prague Czech Republic",
    "Rome Italy",
    "Milan Italy",
    "Venice Italy",
    "Barcelona Spain",
    "Madrid Spain",
    "Lisbon Portugal",
    "Porto Portugal",
    "Amsterdam Netherlands",
    "Brussels Belgium",
    "Copenhagen Denmark",
    "Stockholm Sweden",
    "Oslo Norway",
    "Helsinki Finland",
    "Dublin Ireland",
    "Athens Greece",
    "Istanbul Turkey",
    "Tokyo Japan",
    "Kyoto Japan",
    "Seoul South Korea",
    "Bangkok Thailand",
    "Singapore",
    "Sydney Australia",
    "Melbourne Australia",
    "New York USA",
    "San Francisco USA",
    "Los Angeles USA",
    "Chicago USA",
    "Toronto Canada",
    "Vancouver Canada",
    "Mexico City Mexico",
    "Buenos Aires Argentina",
    "Rio de Janeiro Brazil",
    "Cape Town South Africa",
    "Marrakech Morocco",
    "Dubai UAE",
    "Hong Kong",
    "Taipei Taiwan",
    "Bali Indonesia",
    "Reykjavik Iceland",
    "Budapest Hungary",
    "Warsaw Poland",
    "Krakow Poland",
    "Zagreb Croatia",
    "Dubrovnik Croatia",
    "Valletta Malta",
    "Luxembourg City",
    "Monaco",
    "Andorra la Vella",
    "Tallinn Estonia",
    "Riga Latvia",
    "Vilnius Lithuania",
    "Bratislava Slovakia",
    "Ljubljana Slovenia",
    "Sarajevo Bosnia",
    "Belgrade Serbia",
    "Bucharest Romania",
    "Sofia Bulgaria",
    "Tbilisi Georgia",
    "Yerevan Armenia",
    "Baku Azerbaijan",
    "Almaty Kazakhstan",
    "Ulaanbaatar Mongolia",
    "Kathmandu Nepal",
    "Colombo Sri Lanka",
    "Maldives resort",
    "Phuket Thailand",
    "Hanoi Vietnam",
    "Ho Chi Minh City Vietnam",
    "Kuala Lumpur Malaysia",
    "Jakarta Indonesia",
    "Manila Philippines",
    "Auckland New Zealand",
    "Queenstown New Zealand",
    "Fiji Nadi",
    "Honolulu Hawaii",
    "Las Vegas USA",
    "Miami USA",
    "Boston USA",
    "Washington DC USA",
    "Seattle USA",
    "Denver USA",
    "Austin USA",
    "Nashville USA",
    "New Orleans USA",
    "Montreal Canada",
    "Quebec City Canada",
    "Cartagena Colombia",
    "Lima Peru",
    "Cusco Peru",
    "Santiago Chile",
    "Patagonia Argentina",
    "Galapagos Ecuador",
    "Reykjavik hotel",
    "Swiss Alps hotel",
    "Paris hotel Marais",
    "London hotel Soho",
    "Tokyo hotel Shinjuku",
    "Barcelona hotel Gothic",
    "Rome hotel Trastevere",
    "Amsterdam hotel canal",
    "Berlin hotel Mitte",
    "Vienna hotel Ring",
    "Prague hotel Old Town",
    "Budapest hotel Danube",
    "Lisbon hotel Alfama",
    "Porto hotel Ribeira",
    "Dublin hotel Temple Bar",
    "Edinburgh hotel Royal Mile",
    "Copenhagen hotel Nyhavn",
    "Stockholm hotel Gamla Stan",
    "Helsinki hotel harbor",
    "Oslo hotel fjord",
    "Bergen Norway",
    "Flam Norway",
    "Chamonix France",
    "Nice France",
    "Cannes France",
    "Provence France",
    "Tuscany Italy",
    "Amalfi Coast Italy",
    "Santorini Greece",
    "Mykonos Greece",
    "Crete Greece",
    "Rhodes Greece",
    "Malaga Spain",
    "Seville Spain",
    "Granada Spain",
    "Bilbao Spain",
    "San Sebastian Spain",
    "Porto wine region",
    "Bruges Belgium",
    "Ghent Belgium",
    "Strasbourg France",
    "Colmar France",
    "Salzburg Austria",
    "Innsbruck Austria",
    "Hallstatt Austria",
    "Lake Como Italy",
    "Venice Lido",
    "Florence Italy",
    "Naples Italy",
    "Palermo Italy",
    "Catania Italy",
    "Malta Gozo",
    "Cyprus Limassol",
    "Tel Aviv Israel",
    "Jerusalem Israel",
    "Petra Jordan",
    "Marrakech riad",
    "Fes Morocco",
    "Cairo Egypt",
    "Luxor Egypt",
    "Nairobi Kenya",
    "Serengeti Tanzania",
    "Victoria Falls",
    "Mauritius resort",
    "Seychelles beach",
    "Mumbai India",
    "Delhi India",
    "Jaipur India",
    "Goa India",
    "Beijing China",
    "Shanghai China",
    "Guangzhou China",
    "Shenzhen China",
    "Osaka Japan",
    "Hiroshima Japan",
    "Sapporo Japan",
    "Busan South Korea",
    "Jeju South Korea",
    "Taipei 101",
    "Macau",
    "Phnom Penh Cambodia",
    "Siem Reap Cambodia",
    "Luang Prabang Laos",
    "Vientiane Laos",
    "Yangon Myanmar",
    "Dhaka Bangladesh",
    "Islamabad Pakistan",
    "Lahore Pakistan",
    "Tashkent Uzbekistan",
    "Samarkand Uzbekistan",
    "Tehran Iran",
    "Muscat Oman",
    "Doha Qatar",
    "Abu Dhabi UAE",
    "Riyadh Saudi Arabia",
    "Jeddah Saudi Arabia",
    "Addis Ababa Ethiopia",
    "Lagos Nigeria",
    "Accra Ghana",
    "Casablanca Morocco",
    "Tunis Tunisia",
    "Algiers Algeria",
    "Reunion Island",
    "Madagascar Antananarivo",
    "Zanzibar Tanzania",
    "Kigali Rwanda",
    "Windhoek Namibia",
    "Gaborone Botswana",
    "Maputo Mozambique",
    "Lusaka Zambia",
    "Harare Zimbabwe",
    "Kampala Uganda",
    "Dakar Senegal",
    "Abidjan Ivory Coast",
    "Libreville Gabon",
    "Douala Cameroon",
    "Kinshasa DRC",
    "Luanda Angola",
    "Saint Petersburg Russia",
    "Moscow Russia",
    "Kazan Russia",
    "Tallinn old town hotel",
    "Riga art nouveau",
    "Helsinki design hotel",
    "Bergen fish market",
    "Geiranger Norway",
    "Lofoten Norway",
    "Faroe Islands",
    "Greenland Nuuk",
    "Reykjavik Blue Lagoon",
    "Scottish Highlands",
    "Isle of Skye",
    "Lake District UK",
    "Cotswolds UK",
    "Cornwall UK",
    "Normandy France",
    "Brittany France",
    "Loire Valley France",
    "Bordeaux France",
    "Lyon gastronomy",
    "Dijon France",
    "Annecy France",
    "Grenoble France",
    "Toulouse France",
    "Biarritz France",
    "San Marino",
    "Vatican City Rome",
    "Pompeii Italy",
    "Cinque Terre Italy",
    "Dolomites Italy",
    "Sicily Etna",
    "Sardinia Italy",
    "Corsica France",
    "Ibiza Spain",
    "Mallorca Spain",
    "Tenerife Spain",
    "Madeira Portugal",
    "Azores Portugal",
    "Gibraltar",
    "Channel Islands Jersey",
    "Isle of Man",
    "Guernsey",
    "Liechtenstein Vaduz",
    "Appenzell Switzerland",
    "Grindelwald Switzerland",
    "St Moritz Switzerland",
    "Davos Switzerland",
    "Lucerne Switzerland",
    "Rhine Falls Switzerland",
    "Black Forest Germany",
    "Rothenburg Germany",
    "Neuschwanstein Germany",
    "Cologne Germany",
    "Frankfurt Germany",
    "Leipzig Germany",
    "Dresden Germany",
    "Nuremberg Germany",
    "Heidelberg Germany",
    "Brno Czech Republic",
    "Cesky Krumlov Czech Republic",
    "Split Croatia",
    "Hvar Croatia",
    "Plitvice Croatia",
    "Kotor Montenegro",
    "Mostar Bosnia",
    "Skopje North Macedonia",
    "Thessaloniki Greece",
    "Meteora Greece",
    "Kotor bay",
    "Lake Bled Slovenia",
    "Piran Slovenia",
    "Transylvania Romania",
    "Bran Castle Romania",
    "Chernobyl tour Ukraine",
    "Lviv Ukraine",
    "Odessa Ukraine",
    "Minsk Belarus",
    "Tirana Albania",
    "Santander Spain",
    "Girona Spain",
    "Toledo Spain",
    "Cordoba Spain",
    "Ronda Spain",
    "Cadiz Spain",
    "Evora Portugal",
    "Sintra Portugal",
    "Coimbra Portugal",
    "Braga Portugal",
    "Faro Portugal",
    "Lagos Portugal Algarve",
    "Biarritz surf",
    "Chamonix Mont Blanc",
    "Megève France ski",
    "Verbier Switzerland ski",
    "Zermatt Matterhorn",
    "Jungfrau region",
    "Arosa Switzerland",
    "St Anton Austria ski",
    "Kitzbuhel Austria",
    "Innsbruck ski",
    "Bansko Bulgaria ski",
    "Tromso Norway aurora",
    "Lapland Finland",
    "Rovaniemi Finland",
    "Kiruna Sweden",
    "Abisko Sweden",
    "Reykjavik northern lights",
    "Faroe Torshavn",
    "Svalbard Longyearbyen",
    "Anchorage Alaska",
    "Juneau Alaska",
    "Yellowknife Canada aurora",
    "Churchill Canada polar bears",
    "Banff Canada",
    "Jasper Canada",
    "Whistler Canada",
    "Tofino Canada",
    "Niagara Falls Canada",
    "Quebec winter carnival",
    "Halifax Canada",
    "St Johns Newfoundland",
    "Savannah USA",
    "Charleston USA",
    "Asheville USA",
    "Santa Fe USA",
    "Sedona USA",
    "Grand Canyon USA",
    "Yellowstone USA",
    "Yosemite USA",
    "Lake Tahoe USA",
    "Napa Valley USA",
    "Portland Oregon USA",
    "Salt Lake City USA",
    "Santa Barbara USA",
    "Key West USA",
    "Charleston SC",
    "Nashville music",
    "Memphis USA",
    "Santa Cruz California",
    "Palm Springs USA",
    "Joshua Tree USA",
    "Death Valley USA",
    "Monterey California",
    "Carmel California",
    "Big Sur California",
    "Lake Powell USA",
    "Antelope Canyon USA",
    "Zion National Park USA",
    "Bryce Canyon USA",
    "Arches National Park USA",
    "Moab Utah",
    "Telluride Colorado",
    "Aspen Colorado",
    "Vail Colorado",
    "Park City Utah",
    "Jackson Hole Wyoming",
    "Glacier National Park USA",
    "Acadia Maine",
    "Outer Banks North Carolina",
    "Mackinac Island Michigan",
    "Door County Wisconsin",
    "Lake Michigan Chicago",
    "Hudson Valley New York",
    "Hamptons New York",
    "Cape Cod Massachusetts",
    "Bar Harbor Maine",
    "Burlington Vermont",
    "White Mountains New Hampshire",
    "Adirondacks New York",
    "Finger Lakes New York",
    "Niagara USA",
    "Puerto Rico San Juan",
    "Havana Cuba",
    "Cancun Mexico",
    "Tulum Mexico",
    "Oaxaca Mexico",
    "Guadalajara Mexico",
    "San Miguel de Allende Mexico",
    "Playa del Carmen Mexico",
    "Cabo San Lucas Mexico",
    "Panama City Panama",
    "Costa Rica San Jose",
    "La Fortuna Costa Rica",
    "Monteverde Costa Rica",
    "Belize City",
    "Guatemala Antigua",
    "Roatan Honduras",
    "Roatan diving",
    "San Juan del Sur Nicaragua",
    "Bocas del Toro Panama",
    "Galapagos cruise",
    "Easter Island Chile",
    "Uyuni Bolivia salt flats",
    "Machu Picchu Peru",
    "Amazon Manaus Brazil",
    "Iguazu Falls",
    "Punta del Este Uruguay",
    "Montevideo Uruguay",
    "Valparaiso Chile",
    "Atacama Chile",
    "Easter Island hotel",
    "Fernando de Noronha Brazil",
    "Salvador Brazil",
    "Recife Brazil",
    "Fortaleza Brazil",
    "Brasilia Brazil",
    "Florianopolis Brazil",
    "Punta Arenas Chile",
    "Torres del Paine Chile",
    "Patagonia El Calafate",
    "Bariloche Argentina",
    "Mendoza Argentina wine",
    "Ushuaia Argentina",
    "Antarctica cruise departure",
    "Falkland Islands",
    "South Georgia expedition",
    "Reykjavik airport hotel",
    "Keflavik Iceland",
    "Akureyri Iceland",
    "Reykjanes peninsula",
    "Golden Circle Iceland",
    "South Coast Iceland",
    "Westfjords Iceland",
    "Faroe Mykines",
    "Shetland Islands",
    "Orkney Islands",
    "Outer Hebrides Scotland",
    "Islay Scotland whisky",
    "Speyside Scotland",
    "Dingle Ireland",
    "Galway Ireland",
    "Cork Ireland",
    "Killarney Ireland",
    "Belfast Northern Ireland",
    "Giant's Causeway",
    "Isle of Wight",
    "Jersey Channel Islands hotel",
    "Guernsey hotel",
    "Mont Saint Michel France",
    "Chartres France",
    "Versailles France",
    "Fontainebleau France",
    "Reims champagne",
    "Epernay France",
    "Alsace Colmar hotel",
    "Freiburg Germany",
    "Lake Constance Germany",
    "Salzkammergut Austria",
    "Wachau Austria wine",
    "Graz Austria",
    "Linz Austria",
    "Bratislava castle hotel",
    "Budapest thermal baths",
    "Eger Hungary wine",
    "Lake Balaton Hungary",
    "Cluj Romania",
    "Sibiu Romania",
    "Sighisoara Romania",
    "Plovdiv Bulgaria",
    "Sozopol Bulgaria beach",
    "Varna Bulgaria",
    "Batumi Georgia",
    "Kutaisi Georgia",
    "Mtskheta Georgia",
    "Borjomi Georgia",
    "Gudauri Georgia ski",
    "Baku old city hotel",
    "Sheki Azerbaijan",
    "Samarkand Registan hotel",
    "Bukhara Uzbekistan",
    "Khiva Uzbekistan",
    "Almaty mountains",
    "Astana Kazakhstan",
    "Urumqi China",
    "Kashgar China",
    "Lhasa Tibet",
    "Chengdu China",
    "Xi'an China terracotta",
    "Hangzhou China",
    "Suzhou China",
    "Nanjing China",
    "Qingdao China",
    "Dalian China",
    "Harbin China ice festival",
    "Changchun China",
    "Shenyang China",
    "Kunming China",
    "Lijiang China",
    "Dali China",
    "Guilin China",
    "Zhangjiajie China",
    "Huangshan China",
    "Pingyao China",
    "Zhangye China rainbow mountains",
    "Dunhuang China",
    "Ulaanbaatar ger camp",
    "Gobi desert Mongolia",
    "Irkutsk Russia",
    "Lake Baikal Russia",
    "Vladivostok Russia",
    "Kamchatka Russia",
    "Petropavlovsk Russia",
    "Yakutsk Russia",
    "Magadan Russia",
    "Hokkaido Sapporo snow",
    "Niseko Japan ski",
    "Hakone Japan onsen",
    "Nikko Japan",
    "Kanazawa Japan",
    "Takayama Japan",
    "Nara Japan deer park",
    "Hiroshima peace memorial",
    "Miyajima Japan",
    "Fukuoka Japan ramen",
    "Nagasaki Japan",
    "Okinawa Japan beach",
    "Jeju island Korea",
    "Busan Korea seafood",
    "Gyeongju Korea",
    "Andong Korea",
    "DMZ Korea tour",
    "Taipei night market",
    "Taichung Taiwan",
    "Tainan Taiwan",
    "Kaohsiung Taiwan",
    "Hualien Taiwan gorge",
    "Penghu Taiwan",
    "Hong Kong Victoria Peak",
    "Macau Cotai",
    "Shenzhen tech hub",
    "Guangzhou dim sum",
    "Zhuhai China",
    "Hainan Sanya beach",
    "Da Nang Vietnam beach",
    "Hoi An Vietnam lantern",
    "Hue Vietnam imperial",
    "Dalat Vietnam",
    "Nha Trang Vietnam",
    "Phu Quoc Vietnam",
    "Vientiane Laos temple",
    "Vang Vieng Laos",
    "Pakse Laos",
    "4000 Islands Laos",
    "Siem Reap Angkor",
    "Phnom Penh Cambodia",
    "Battambang Cambodia",
    "Koh Rong Cambodia",
    "Bangkok Grand Palace",
    "Chiang Mai Thailand",
    "Chiang Rai Thailand",
    "Pai Thailand",
    "Krabi Thailand",
    "Koh Samui Thailand",
    "Koh Phangan Thailand",
    "Koh Lanta Thailand",
    "Ayutthaya Thailand",
    "Penang Malaysia food",
    "Langkawi Malaysia",
    "Malacca Malaysia",
    "Kota Kinabalu Malaysia",
    "Kuching Malaysia Borneo",
    "Jakarta Indonesia capital",
    "Yogyakarta Indonesia Borobudur",
    "Bandung Indonesia",
    "Surabaya Indonesia",
    "Lombok Indonesia",
    "Komodo Indonesia",
    "Raja Ampat Indonesia",
    "Boracay Philippines",
    "Cebu Philippines",
    "Palawan Philippines",
    "El Nido Philippines",
    "Manila Intramuros",
    "Davao Philippines",
    "Bohol Philippines chocolate hills",
    "Siquijor Philippines",
    "Port Moresby Papua New Guinea",
    "Honiara Solomon Islands",
    "Port Vila Vanuatu",
    "Noumea New Caledonia",
    "Suva Fiji capital",
    "Apia Samoa",
    "Nuku'alofa Tonga",
    "Papeete Tahiti",
    "Bora Bora French Polynesia",
    "Moorea French Polynesia",
    "Rarotonga Cook Islands",
    "Aitutaki Cook Islands",
    "Nadi Fiji resort",
    "Denarau Fiji",
    "Queenstown adventure",
    "Rotorua New Zealand geothermal",
    "Wellington New Zealand",
    "Christchurch New Zealand",
    "Milford Sound New Zealand",
    "Franz Josef glacier",
    "Tasmania Australia",
    "Perth Australia",
    "Adelaide Australia",
    "Brisbane Australia",
    "Gold Coast Australia",
    "Cairns Great Barrier Reef",
    "Darwin Australia",
    "Uluru Australia",
    "Alice Springs Australia",
    "Hobart Australia",
    "Canberra Australia",
    "Blue Mountains Australia",
    "Great Ocean Road Australia",
    "Kangaroo Island Australia",
    "Rottnest Island Australia",
    "Broome Australia",
    "Exmouth Australia whale shark",
    "Hamilton Island Australia",
    "Lord Howe Island Australia",
    "Norfolk Island",
    "New Caledonia Isle of Pines",
    "Reunion cirque",
    "Saint Denis Reunion",
    "Port Louis Mauritius",
    "Victoria Seychelles",
    "Praslin Seychelles",
    "Mahe Seychelles",
    "Malé Maldives",
    "Maafushi Maldives",
    "Colombo Sri Lanka tea",
    "Galle Sri Lanka fort",
    "Kandy Sri Lanka temple",
    "Sigiriya Sri Lanka rock",
    "Ella Sri Lanka train",
    "Kathmandu Durbar Square",
    "Pokhara Nepal lakes",
    "Chitwan Nepal safari",
    "Lhasa Potala Palace",
    "Thimphu Bhutan",
    "Paro Bhutan tiger nest",
    "Dhaka Bangladesh old city",
    "Sylhet Bangladesh tea",
    "Islamabad Pakistan capital",
    "Lahore Pakistan fort",
    "Karachi Pakistan",
    "Multan Pakistan",
    "Peshawar Pakistan",
    "Kabul Afghanistan",
    "Herat Afghanistan",
    "Mazar-i-Sharif Afghanistan",
    "Tehran Iran capital",
    "Isfahan Iran mosque",
    "Shiraz Iran gardens",
    "Yazd Iran desert city",
    "Persepolis Iran ruins",
    "Tabriz Iran bazaar",
    "Mashhad Iran shrine",
    "Baghdad Iraq",
    "Erbil Iraq Kurdistan",
    "Basra Iraq",
    "Damascus Syria",
    "Aleppo Syria",
    "Beirut Lebanon corniche",
    "Byblos Lebanon ancient",
    "Baalbek Lebanon ruins",
    "Amman Jordan citadel",
    "Petra Jordan treasury",
    "Wadi Rum Jordan desert",
    "Aqaba Jordan Red Sea",
    "Dead Sea Jordan",
    "Jerusalem Old City",
    "Tel Aviv beach",
    "Haifa Israel",
    "Eilat Israel Red Sea",
    "Nazareth Israel",
    "Bethlehem West Bank",
    "Ramallah West Bank",
    "Gaza City",
    "Riyadh Saudi capital",
    "Jeddah Saudi Red Sea",
    "Mecca Saudi Arabia",
    "Medina Saudi Arabia",
    "Al Ula Saudi Arabia",
    "Dubai Burj Khalifa",
    "Abu Dhabi Sheikh Zayed Mosque",
    "Sharjah UAE",
    "Muscat Oman souq",
    "Salalah Oman monsoon",
    "Nizwa Oman fort",
    "Doha Qatar Museum",
    "Kuwait City",
    "Manama Bahrain",
    "Sanaa Yemen",
    "Aden Yemen port",
    "Muscat hotel beach",
]


def infer_seed_category(query: str) -> str:
    haystack = query.lower()
    if any(x in haystack for x in ("hotel", "hostel", "resort", "riad", " lodge", " inn", "suites", "ger camp")):
        return "LODGING"
    if any(x in haystack for x in ("museum", "gallery", "exhibition")):
        return "MUSEUM"
    if any(x in haystack for x in ("café", "cafe", "coffee", "espresso", "bakery")):
        return "CAFE"
    if any(x in haystack for x in ("restaurant", "bistro", "brasserie", "ramen", "dim sum", "gastronomy", "seafood")):
        return "RESTAURANT"
    if any(x in haystack for x in ("national park", " park", "garden", "trail", "forest", "hiking")):
        return "PARK"
    if any(x in haystack for x in ("viewpoint", "lookout", "observation deck", "summit", " vista")):
        return "VIEWPOINT"
    if any(
        x in haystack
        for x in (
            "tower",
            "monument",
            "cathedral",
            "church",
            "mosque",
            "temple",
            "palace",
            "castle",
            "memorial",
            "falls",
            "bridge",
            "market",
            "square",
            "old town",
            "fort",
            "ruins",
            "island",
            "beach",
            "canyon",
            "reef",
            "volcano",
            "louvre",
            "eiffel",
            "angkor",
            "machu picchu",
            "basilica",
            "abbey",
            "colosseum",
            "cruise",
            "cirque",
            "peak",
            "matterhorn",
            "fjord",
            "lagoon",
            "blue lagoon",
            "grand palace",
            "night market",
        )
    ):
        return "TOURIST_ATTRACTION"
    return "CITY"


def map_primary_type(primary_type: str | None) -> str | None:
    if not primary_type:
        return None
    mapping = {
        "cafe": "CAFE",
        "coffee_shop": "CAFE",
        "bakery": "CAFE",
        "restaurant": "RESTAURANT",
        "museum": "MUSEUM",
        "art_gallery": "MUSEUM",
        "park": "PARK",
        "tourist_attraction": "TOURIST_ATTRACTION",
        "lodging": "LODGING",
        "hotel": "LODGING",
    }
    return mapping.get(primary_type.lower())


def build_search_entries() -> list[dict[str, str]]:
    entries: list[dict[str, str]] = []
    for query in SEARCH_QUERIES:
        entries.append({"query": query, "seedCategory": infer_seed_category(query)})
    for city in POI_CITY_ANCHORS:
        city_label = city.split()[0]
        entries.extend(
            [
                {"query": f"cafe {city}", "seedCategory": "CAFE"},
                {"query": f"museum {city}", "seedCategory": "MUSEUM"},
                {"query": f"restaurant {city}", "seedCategory": "RESTAURANT"},
                {"query": f"tourist attraction {city_label}", "seedCategory": "TOURIST_ATTRACTION"},
                {"query": f"park {city_label}", "seedCategory": "PARK"},
                {"query": f"viewpoint {city_label}", "seedCategory": "VIEWPOINT"},
            ]
        )
    return entries


def synthetic_places(count: int) -> list[dict]:
    places: list[dict] = []
    categories = ["CITY", "LODGING", "CAFE", "RESTAURANT", "MUSEUM", "TOURIST_ATTRACTION", "PARK", "VIEWPOINT"]
    for i in range(count):
        pid = f"ChIJ_SeedPlace{i:04d}"
        city_idx = i % 40
        category = categories[i % len(categories)]
        places.append(
            {
                "googlePlaceId": pid,
                "placeName": f"Seed {category.title()} {i}",
                "cityName": f"Seed City {city_idx}",
                "formattedAddress": f"{i} Seed Street, Seed City {city_idx}",
                "latitude": 40.0 + city_idx * 0.08 + (i % 7) * 0.01,
                "longitude": -74.0 + city_idx * 0.08 + (i % 5) * 0.01,
                "countryCode": "US" if city_idx % 3 == 0 else ("CH" if city_idx % 3 == 1 else "FR"),
                "seedCategory": category,
            }
        )
    return places


def http_get_json(
    url: str, headers: dict[str, str] | None = None, timeout: float = 60.0
) -> object:
    merged = {"Accept": "application/json"}
    if headers:
        merged.update(headers)
    req = urllib.request.Request(url, headers=merged)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def normalize_place_id(place_id: str) -> str:
    trimmed = str(place_id).strip()
    if trimmed.startswith("places/"):
        return trimmed[len("places/") :]
    return trimmed


def place_row_from_location_pack(
    details: dict[str, Any], place_id: str, query: str, seed_category: str
) -> dict:
    """Map ``PlaceDetailsResult`` from ``/internal/location-pack``."""
    lat = details.get("lat", details.get("latitude"))
    lon = details.get("lon", details.get("longitude"))
    primary_type = details.get("primaryType")
    resolved_category = map_primary_type(primary_type) or seed_category
    return {
        "googlePlaceId": normalize_place_id(place_id),
        "placeName": (details.get("placeName") or "").strip() or query,
        "cityName": (details.get("cityName") or "").strip() or query,
        "formattedAddress": (details.get("formattedAddress") or "").strip() or query,
        "latitude": float(lat if lat is not None else 0.0),
        "longitude": float(lon if lon is not None else 0.0),
        "countryCode": (details.get("countryCode") or "XX").strip().upper() or "XX",
        "seedCategory": resolved_category,
    }


def place_row_from_search_hit(hit: dict[str, Any], query: str, seed_category: str) -> dict:
    """Fallback when location-pack is unavailable (search has no country/city)."""
    place_id = hit.get("placeId") or hit.get("googlePlaceId") or hit.get("id") or ""
    return {
        "googlePlaceId": normalize_place_id(str(place_id)),
        "placeName": (hit.get("placeName") or query).strip(),
        "cityName": (hit.get("placeName") or query).strip(),
        "formattedAddress": (hit.get("formattedAddress") or query).strip(),
        "latitude": float(hit.get("lat", hit.get("latitude", 0.0)) or 0.0),
        "longitude": float(hit.get("lon", hit.get("longitude", 0.0)) or 0.0),
        "countryCode": "XX",
        "seedCategory": seed_category,
    }


def print_place_row(index: int, query: str, row: dict, source: str) -> None:
    print(
        f"[{index:4d}] {row['googlePlaceId']}\n"
        f"       query={query!r}  source={source}\n"
        f"       placeName={row['placeName']!r}\n"
        f"       cityName={row['cityName']!r}\n"
        f"       formattedAddress={row['formattedAddress']!r}\n"
        f"       lat={row['latitude']}, lon={row['longitude']}, country={row['countryCode']}",
        flush=True,
    )


def fetch_live(
    api_base: str,
    internal_base: str,
    internal_secret: str,
    entries: list[dict[str, str]],
    *,
    min_places: int = 100,
) -> list[dict]:
    origin = api_base.rstrip("/")
    search_base = origin if origin.endswith("/api/v2") else f"{origin}/api/v2"
    internal_origin = internal_base.rstrip("/")

    places_by_id: dict[str, dict] = {}
    failed: list[str] = []

    print(f"Search API:    {search_base}/external/details/search")
    print(f"Details API:   {internal_origin}/internal/location-pack")
    print(f"Queries:       {len(entries)}")
    print("-" * 72)

    for idx, entry in enumerate(entries, start=1):
        q = entry["query"]
        seed_category = entry["seedCategory"]
        enc = urllib.parse.quote(q)
        search_url = f"{search_base}/external/details/search?q={enc}"
        try:
            data = http_get_json(search_url)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
            failed.append(f"{q}: search failed ({e})")
            print(f"[{idx:4d}] FAILED search  query={q!r}  error={e}", file=sys.stderr)
            continue

        candidates = data if isinstance(data, list) else data.get("results") or data.get("items") or []
        if not candidates:
            failed.append(f"{q}: no search results")
            print(f"[{idx:4d}] FAILED search  query={q!r}  error=no results", file=sys.stderr)
            continue

        first = candidates[0]
        if not isinstance(first, dict):
            failed.append(f"{q}: unexpected search hit type")
            continue

        place_id = first.get("placeId") or first.get("googlePlaceId") or first.get("id")
        if not place_id:
            failed.append(f"{q}: missing placeId in search result")
            continue

        normalized_id = normalize_place_id(str(place_id))
        source = "location-pack"
        row: dict | None = None

        pack_url = (
            f"{internal_origin}/internal/location-pack?"
            f"placeId={urllib.parse.quote(normalized_id)}&fresh=true"
        )
        try:
            details = http_get_json(
                pack_url, headers={"X-Internal-Secret": internal_secret}
            )
            if isinstance(details, dict) and details:
                row = place_row_from_location_pack(details, normalized_id, q, seed_category)
        except urllib.error.HTTPError as e:
            body = ""
            try:
                body = e.read().decode("utf-8", errors="replace")[:200]
            except OSError:
                pass
            failed.append(f"{q}: location-pack HTTP {e.code} ({body})")
            source = "search-fallback"
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
            failed.append(f"{q}: location-pack failed ({e})")
            source = "search-fallback"

        if row is None:
            row = place_row_from_search_hit(first, q, seed_category)
            if source != "search-fallback":
                source = "search-fallback"

        places_by_id[row["googlePlaceId"]] = row
        print_place_row(idx, q, row, source)

    print("-" * 72)
    print(f"Fetched {len(places_by_id)} unique places ({len(failed)} query failures)")

    if failed:
        print(f"\nWarning: {len(failed)} queries failed (first 10):", file=sys.stderr)
        for line in failed[:10]:
            print(f"  - {line}", file=sys.stderr)

    if len(places_by_id) < min_places:
        raise SystemExit(
            f"Only {len(places_by_id)} places fetched; need at least {min_places}. "
            "Check ingress :8080, external-info :8082 port-forward, GOOGLE_MAPS_API_KEY, "
            "and TRIPPLANNING_INTERNAL_SECRET, or use --synthetic."
        )
    return list(places_by_id.values())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--api-base",
        default=os.environ.get("TRIPPLANNING_API_BASE", "http://localhost:8080"),
        help="Trip ingress origin for /api/v2/external/details/search",
    )
    parser.add_argument(
        "--internal-base",
        default=os.environ.get("EXTERNAL_INFO_INTERNAL_BASE", "http://localhost:8082"),
        help="external-info-service origin for /internal/location-pack",
    )
    parser.add_argument(
        "--internal-secret",
        default=os.environ.get("TRIPPLANNING_INTERNAL_SECRET", "dev-internal-service-secret"),
        help="X-Internal-Secret header value",
    )
    parser.add_argument("--synthetic", action="store_true", help="Generate placeholder places offline")
    parser.add_argument("--synthetic-count", type=int, default=600)
    parser.add_argument(
        "--test",
        action="store_true",
        help="Fetch only 10 search queries (smoke test; min 10 places required)",
    )
    parser.add_argument("--out", type=Path, default=OUT_PATH)
    args = parser.parse_args()

    if args.synthetic:
        places = synthetic_places(args.synthetic_count if not args.test else 10)
        for i, row in enumerate(places, start=1):
            print_place_row(i, f"synthetic-{i}", row, "synthetic")
    else:
        all_entries = build_search_entries()
        entries = all_entries[:10] if args.test else all_entries
        min_places = 10 if args.test else 100
        places = fetch_live(
            args.api_base,
            args.internal_base,
            args.internal_secret,
            entries,
            min_places=min_places,
        )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(places, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(places)} places to {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
