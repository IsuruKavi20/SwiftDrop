import { test, expect } from '@playwright/test';
import {
  resolveDistrict,
  SRI_LANKA_DISTRICTS,
  isValidDistrict,
  CITY_TO_DISTRICT_MAP,
} from '../src/features/merchant/utils/districtUtils';

test.describe('District Resolution Utilities', () => {
  test('maps known non-district cities to their canonical district', () => {
    // Negombo belongs to Gampaha
    expect(resolveDistrict('Negombo', 'Western')).toBe('Gampaha');
    expect(resolveDistrict('negombo', 'Western')).toBe('Gampaha');
    expect(resolveDistrict('Negombo Town', 'Western')).toBe('Gampaha');

    // Peradeniya belongs to Kandy
    expect(resolveDistrict('Peradeniya', 'Central')).toBe('Kandy');
    expect(resolveDistrict('peradeniya', 'Central')).toBe('Kandy');

    // Other key Sri Lankan cities
    expect(resolveDistrict('Panadura', 'Western')).toBe('Kalutara');
    expect(resolveDistrict('Dehiwala', 'Western')).toBe('Colombo');
    expect(resolveDistrict('Moratuwa', 'Western')).toBe('Colombo');
    expect(resolveDistrict('Wattala', 'Western')).toBe('Gampaha');
    expect(resolveDistrict('Dambulla', 'Central')).toBe('Matale');
    expect(resolveDistrict('Hatton', 'Central')).toBe('Nuwara Eliya');
    expect(resolveDistrict('Hikkaduwa', 'Southern')).toBe('Galle');
    expect(resolveDistrict('Weligama', 'Southern')).toBe('Matara');
    expect(resolveDistrict('Tangalle', 'Southern')).toBe('Hambantota');
    expect(resolveDistrict('Point Pedro', 'Northern')).toBe('Jaffna');
    expect(resolveDistrict('Chilaw', 'North Western')).toBe('Puttalam');
    expect(resolveDistrict('Bandarawela', 'Uva')).toBe('Badulla');
    expect(resolveDistrict('Mawanella', 'Sabaragamuwa')).toBe('Kegalle');
  });

  test('resolves direct district-named cities to canonical districts', () => {
    expect(resolveDistrict('Colombo', 'Western')).toBe('Colombo');
    expect(resolveDistrict('kandy', 'Central')).toBe('Kandy');
    expect(resolveDistrict('Galle', 'Southern')).toBe('Galle');
    expect(resolveDistrict('Matara', 'Southern')).toBe('Matara');
    expect(resolveDistrict('Jaffna', 'Northern')).toBe('Jaffna');
    expect(resolveDistrict('Kurunegala', 'North Western')).toBe('Kurunegala');
  });

  test('never treats arbitrary city names as valid districts and leaves them empty', () => {
    // Arbitrary / unknown cities should NEVER be returned as a district
    expect(resolveDistrict('UnknownCity', 'Western')).toBe('');
    expect(resolveDistrict('Springfield', 'Central')).toBe('');
    expect(resolveDistrict('Paris', 'Europe')).toBe('');
    expect(resolveDistrict('Random Village', 'Southern')).toBe('');
    expect(resolveDistrict('Narnia', null)).toBe('');
  });

  test('does not treat broad province names as districts when city is unknown', () => {
    // 'Western' has 3 districts (Colombo, Gampaha, Kalutara) - cannot guess
    expect(resolveDistrict('UnknownTown', 'Western')).toBe('');
    expect(resolveDistrict('UnknownTown', 'Central')).toBe('');
    expect(resolveDistrict('UnknownTown', 'Southern')).toBe('');
  });

  test('resolves district if merchant entered a valid district name in province field', () => {
    // If merchant explicitly typed the district name into province field
    expect(resolveDistrict('UnknownTown', 'Gampaha')).toBe('Gampaha');
    expect(resolveDistrict('UnknownTown', 'Kandy')).toBe('Kandy');
  });

  test('handles null, undefined, or empty inputs gracefully', () => {
    expect(resolveDistrict(null, null)).toBe('');
    expect(resolveDistrict(undefined, undefined)).toBe('');
    expect(resolveDistrict('', '')).toBe('');
    expect(resolveDistrict('   ', '   ')).toBe('');
  });

  test('validates all canonical districts correctly', () => {
    expect(SRI_LANKA_DISTRICTS.length).toBe(25);
    for (const d of SRI_LANKA_DISTRICTS) {
      expect(isValidDistrict(d)).toBe(true);
      expect(isValidDistrict(d.toLowerCase())).toBe(true);
      expect(isValidDistrict(d.toUpperCase())).toBe(true);
    }
    expect(isValidDistrict('Western')).toBe(false);
    expect(isValidDistrict('Negombo')).toBe(false);
    expect(isValidDistrict('Peradeniya')).toBe(false);
    expect(isValidDistrict('UnknownDistrict')).toBe(false);
    expect(isValidDistrict('')).toBe(false);
    expect(isValidDistrict(null)).toBe(false);
  });

  test('verifies all mapped cities target a valid canonical district', () => {
    for (const [city, district] of Object.entries(CITY_TO_DISTRICT_MAP)) {
      expect(
        isValidDistrict(district),
        `City "${city}" maps to invalid district "${district}"`
      ).toBe(true);
    }
  });
});
