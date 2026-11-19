/**
 * Official 25 administrative districts of Sri Lanka.
 */
export const SRI_LANKA_DISTRICTS = [
  'Colombo',
  'Gampaha',
  'Kalutara',
  'Kandy',
  'Matale',
  'Nuwara Eliya',
  'Galle',
  'Matara',
  'Hambantota',
  'Jaffna',
  'Kilinochchi',
  'Mannar',
  'Vavuniya',
  'Mullaitivu',
  'Batticaloa',
  'Ampara',
  'Trincomalee',
  'Kurunegala',
  'Puttalam',
  'Anuradhapura',
  'Polonnaruwa',
  'Badulla',
  'Monaragala',
  'Ratnapura',
  'Kegalle',
] as const;

export type SriLankaDistrict = (typeof SRI_LANKA_DISTRICTS)[number];

const DISTRICT_LOWER_MAP = new Map<string, SriLankaDistrict>(
  SRI_LANKA_DISTRICTS.map((d) => [d.toLowerCase(), d])
);

/**
 * Reliable mapping of Sri Lankan towns, suburbs, and cities to their administrative district.
 * Cities with names identical to districts (e.g., Colombo, Kandy, Galle) are resolved directly via SRI_LANKA_DISTRICTS.
 */
export const CITY_TO_DISTRICT_MAP: Record<string, SriLankaDistrict> = {
  // Western Province - Colombo District
  'dehiwala': 'Colombo',
  'mount lavinia': 'Colombo',
  'mt lavinia': 'Colombo',
  'moratuwa': 'Colombo',
  'ratmalana': 'Colombo',
  'kotte': 'Colombo',
  'sri jayawardenepura kotte': 'Colombo',
  'sri jayewardenepura kotte': 'Colombo',
  'sri jayawardenepura': 'Colombo',
  'rajagiriya': 'Colombo',
  'nawala': 'Colombo',
  'nugegoda': 'Colombo',
  'battaramulla': 'Colombo',
  'malabe': 'Colombo',
  'thalawathugoda': 'Colombo',
  'pelawatte': 'Colombo',
  'maharagama': 'Colombo',
  'kottawa': 'Colombo',
  'pannipitiya': 'Colombo',
  'homagama': 'Colombo',
  'padukka': 'Colombo',
  'piliyandala': 'Colombo',
  'kesbewa': 'Colombo',
  'boralesgamuwa': 'Colombo',
  'kolonnawa': 'Colombo',
  'wellampitiya': 'Colombo',
  'angoda': 'Colombo',
  'mulleriyawa': 'Colombo',
  'hanwella': 'Colombo',
  'avissawella': 'Colombo',
  'kohuwala': 'Colombo',
  'borella': 'Colombo',
  'wellawatte': 'Colombo',
  'bambalapitiya': 'Colombo',
  'kollupitiya': 'Colombo',
  'pettah': 'Colombo',
  'fort': 'Colombo',
  'maradana': 'Colombo',
  'dematagoda': 'Colombo',
  'slave island': 'Colombo',
  'cinnamon gardens': 'Colombo',

  // Western Province - Gampaha District
  'negombo': 'Gampaha',
  'katunayake': 'Gampaha',
  'seeduwa': 'Gampaha',
  'kochchikade': 'Gampaha',
  'kelaniya': 'Gampaha',
  'peliyagoda': 'Gampaha',
  'kiribathgoda': 'Gampaha',
  'wattala': 'Gampaha',
  'ja-ela': 'Gampaha',
  'ja ela': 'Gampaha',
  'kandana': 'Gampaha',
  'ragama': 'Gampaha',
  'kadawatha': 'Gampaha',
  'biyagama': 'Gampaha',
  'delgoda': 'Gampaha',
  'minuwangoda': 'Gampaha',
  'divulapitiya': 'Gampaha',
  'veyangoda': 'Gampaha',
  'nittambuwa': 'Gampaha',
  'mirigama': 'Gampaha',
  'ganemulla': 'Gampaha',
  'sapugaskanda': 'Gampaha',
  'makola': 'Gampaha',
  'yakkala': 'Gampaha',
  'dompe': 'Gampaha',
  'pugoda': 'Gampaha',
  'mahabage': 'Gampaha',

  // Western Province - Kalutara District
  'panadura': 'Kalutara',
  'wadduwa': 'Kalutara',
  'horana': 'Kalutara',
  'bandaragama': 'Kalutara',
  'beruwala': 'Kalutara',
  'aluthgama': 'Kalutara',
  'matugama': 'Kalutara',
  'mathugama': 'Kalutara',
  'ingiriya': 'Kalutara',
  'payagala': 'Kalutara',
  'maggona': 'Kalutara',
  'agalawatta': 'Kalutara',
  'bulathsinhala': 'Kalutara',
  'nebodha': 'Kalutara',
  'neboda': 'Kalutara',

  // Central Province - Kandy District
  'peradeniya': 'Kandy',
  'gampola': 'Kandy',
  'katugastota': 'Kandy',
  'kundasale': 'Kandy',
  'digana': 'Kandy',
  'teldeniya': 'Kandy',
  'akurana': 'Kandy',
  'wattegama': 'Kandy',
  'menikhinna': 'Kandy',
  'nawalapitiya': 'Kandy',
  'pilimathalawa': 'Kandy',
  'kadugannawa': 'Kandy',
  'ampitiya': 'Kandy',
  'gelioya': 'Kandy',
  'talatuoya': 'Kandy',

  // Central Province - Matale District
  'dambulla': 'Matale',
  'sigiriya': 'Matale',
  'galewela': 'Matale',
  'naula': 'Matale',
  'rattota': 'Matale',
  'ukuwela': 'Matale',
  'pallepola': 'Matale',

  // Central Province - Nuwara Eliya District
  'hatton': 'Nuwara Eliya',
  'talawakele': 'Nuwara Eliya',
  'kotagala': 'Nuwara Eliya',
  'nanu oya': 'Nuwara Eliya',
  'norwood': 'Nuwara Eliya',
  'bogawantalawa': 'Nuwara Eliya',
  'maskeliya': 'Nuwara Eliya',
  'ginigathena': 'Nuwara Eliya',
  'dickoya': 'Nuwara Eliya',
  'walapane': 'Nuwara Eliya',

  // Southern Province - Galle District
  'hikkaduwa': 'Galle',
  'ambalangoda': 'Galle',
  'bentota': 'Galle',
  'baddegama': 'Galle',
  'elpitiya': 'Galle',
  'karapitiya': 'Galle',
  'ahangama': 'Galle',
  'koggala': 'Galle',
  'unawatuna': 'Galle',
  'balapitiya': 'Galle',
  'batapola': 'Galle',
  'neluwa': 'Galle',

  // Southern Province - Matara District
  'weligama': 'Matara',
  'mirissa': 'Matara',
  'akuressa': 'Matara',
  'dikwella': 'Matara',
  'dickwella': 'Matara',
  'hakmana': 'Matara',
  'kamburupitiya': 'Matara',
  'deniyaya': 'Matara',
  'devinuwara': 'Matara',
  'dondra': 'Matara',
  'gandara': 'Matara',
  'thihagoda': 'Matara',

  // Southern Province - Hambantota District
  'tangalle': 'Hambantota',
  'beliatta': 'Hambantota',
  'tissamaharama': 'Hambantota',
  'ambalantota': 'Hambantota',
  'ranna': 'Hambantota',
  'weeraketiya': 'Hambantota',
  'walasmulla': 'Hambantota',
  'suriyawewa': 'Hambantota',
  'middeniya': 'Hambantota',

  // Northern Province - Jaffna District
  'chavakachcheri': 'Jaffna',
  'point pedro': 'Jaffna',
  'valvettithurai': 'Jaffna',
  'chunnakam': 'Jaffna',
  'manipay': 'Jaffna',
  'vaddukoddai': 'Jaffna',
  'nallur': 'Jaffna',
  'kankesanthurai': 'Jaffna',
  'kopay': 'Jaffna',
  'karaveddy': 'Jaffna',
  'tellippalai': 'Jaffna',
  'kodikamam': 'Jaffna',

  // Northern Province - Kilinochchi, Mannar, Vavuniya, Mullaitivu
  'paranthan': 'Kilinochchi',
  'pallai': 'Kilinochchi',
  'pooneryn': 'Kilinochchi',
  'pesalai': 'Mannar',
  'talaimannar': 'Mannar',
  'murunkan': 'Mannar',
  'nedunkeni': 'Vavuniya',
  'cheddikulam': 'Vavuniya',
  'puthukkudiyiruppu': 'Mullaitivu',
  'mankulam': 'Mullaitivu',

  // Eastern Province - Batticaloa, Ampara, Trincomalee
  'kattankudy': 'Batticaloa',
  'eravur': 'Batticaloa',
  'valachchenai': 'Batticaloa',
  'chenkalady': 'Batticaloa',
  'kaluwanchikudy': 'Batticaloa',
  'kalmunai': 'Ampara',
  'sammanthurai': 'Ampara',
  'akkaraipattu': 'Ampara',
  'pottuvil': 'Ampara',
  'arugam bay': 'Ampara',
  'sainthamaruthu': 'Ampara',
  'nintavur': 'Ampara',
  'kinniya': 'Trincomalee',
  'mutur': 'Trincomalee',
  'kantale': 'Trincomalee',
  'nilaveli': 'Trincomalee',

  // North Western Province - Kurunegala, Puttalam
  'kuliyapitiya': 'Kurunegala',
  'pannala': 'Kurunegala',
  'narammala': 'Kurunegala',
  'giriulla': 'Kurunegala',
  'alawwa': 'Kurunegala',
  'polgahawela': 'Kurunegala',
  'mawathagama': 'Kurunegala',
  'ibbagamuwa': 'Kurunegala',
  'wariyapola': 'Kurunegala',
  'nikaweratiya': 'Kurunegala',
  'maho': 'Kurunegala',
  'chilaw': 'Puttalam',
  'wennappuwa': 'Puttalam',
  'marawila': 'Puttalam',
  'dankotuwa': 'Puttalam',
  'nattandiya': 'Puttalam',
  'anamaduwa': 'Puttalam',
  'kalpitiya': 'Puttalam',

  // North Central Province - Anuradhapura, Polonnaruwa
  'kekirawa': 'Anuradhapura',
  'medawachchiya': 'Anuradhapura',
  'eppawala': 'Anuradhapura',
  'tambuttegama': 'Anuradhapura',
  'mihintale': 'Anuradhapura',
  'habarana': 'Anuradhapura',
  'kaduruwela': 'Polonnaruwa',
  'hingurakgoda': 'Polonnaruwa',
  'medirigiriya': 'Polonnaruwa',
  'minneriya': 'Polonnaruwa',

  // Uva Province - Badulla, Monaragala
  'bandarawela': 'Badulla',
  'haputale': 'Badulla',
  'diyatalawa': 'Badulla',
  'ella': 'Badulla',
  'hali ela': 'Badulla',
  'welimada': 'Badulla',
  'mahiyanganaya': 'Badulla',
  'passara': 'Badulla',
  'wellawaya': 'Monaragala',
  'buttala': 'Monaragala',
  'bibile': 'Monaragala',
  'kataragama': 'Monaragala',

  // Sabaragamuwa Province - Ratnapura, Kegalle
  'balangoda': 'Ratnapura',
  'pelmadulla': 'Ratnapura',
  'embilipitiya': 'Ratnapura',
  'kuruwita': 'Ratnapura',
  'kahawatta': 'Ratnapura',
  'eheliyagoda': 'Ratnapura',
  'mawanella': 'Kegalle',
  'warakapola': 'Kegalle',
  'ruwanwella': 'Kegalle',
  'yatiyantota': 'Kegalle',
  'rambukkana': 'Kegalle',
  'deraniyagala': 'Kegalle',
};

/**
 * Normalizes a place name for lookup by stripping punctuation and redundant whitespace.
 */
function normalizePlaceName(val: string): string {
  return val
    .trim()
    .toLowerCase()
    .replace(/[,\-./#]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Resolves a district from a city and/or province with high confidence.
 *
 * Rules:
 * 1. If city matches a canonical district name (e.g. "Colombo", "Kandy", "Galle"), returns that district.
 * 2. If city matches a known town/suburb mapping, returns its verified district (e.g. Negombo -> Gampaha, Peradeniya -> Kandy).
 * 3. Handles common suffixes (e.g. "Negombo Town" -> "Gampaha", "Colombo 03" -> "Colombo").
 * 4. If province was entered as a valid district name, uses that district.
 * 5. If the district cannot be determined with confidence, returns '' (empty string). NEVER returns an arbitrary city name.
 */
export function resolveDistrict(city?: string | null, province?: string | null): string {
  if (!city && !province) return '';

  const cleanCity = normalizePlaceName(city ?? '');
  const cleanProv = normalizePlaceName(province ?? '');

  if (cleanCity) {
    // 1. Direct match with a canonical district name
    const directDistrict = DISTRICT_LOWER_MAP.get(cleanCity);
    if (directDistrict) return directDistrict;

    // 2. Direct match with city-to-district dictionary
    const mappedDistrict = CITY_TO_DISTRICT_MAP[cleanCity];
    if (mappedDistrict) return mappedDistrict;

    // 3. Suffix / area stripping (e.g. "Negombo Town" -> "negombo", "Colombo 07" -> "colombo", "Kandy City" -> "kandy")
    const strippedCity = cleanCity
      .replace(/\s+(town|city|south|north|east|west|fort|bazaar|junction)$/i, '')
      .replace(/\s*\d+$/, '')
      .trim();

    if (strippedCity && strippedCity !== cleanCity) {
      const strippedDistrict = DISTRICT_LOWER_MAP.get(strippedCity) || CITY_TO_DISTRICT_MAP[strippedCity];
      if (strippedDistrict) return strippedDistrict;
    }
  }

  // 4. Check if province was specified as a valid district name (merchants sometimes write district under province)
  if (cleanProv) {
    const provDistrict = DISTRICT_LOWER_MAP.get(cleanProv);
    if (provDistrict) return provDistrict;
  }

  // District cannot be determined confidently -> return empty string
  return '';
}

/**
 * Check if a given string is a valid Sri Lankan district.
 */
export function isValidDistrict(district?: string | null): boolean {
  if (!district) return false;
  return DISTRICT_LOWER_MAP.has(district.trim().toLowerCase());
}
