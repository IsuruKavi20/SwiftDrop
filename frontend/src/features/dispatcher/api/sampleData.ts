import type {
  CourierAvailability,
  DeliverySource,
  DeliveryStatus,
  DeliveryType,
  DispatchAddress,
  DispatchParcel,
  DispatchTrackingEvent,
} from './dispatcherTypes';

/**
 * Sample rows for the dispatcher screens while the backend has no dispatcher endpoints yet.
 * They mirror the backend tables: courier, courier_assignment, delivery (+ delivery_address, parcel, tracking_event).
 * Times are relative to when the page loads, so "waiting since" and "today" always look current.
 */

export interface SampleCourier {
  courierId: number;
  name: string;
  phoneNumber: string;
  vehicleType: string | null;
  vehicleNumber: string | null;
  availability: CourierAvailability;
}

export interface SampleAssignment {
  assignmentId: number;
  deliveryId: number;
  courierId: number;
  assignedByName: string;
  assignedAt: string;
  /** TRUE for the current assignment, null for history (never false, like the backend). */
  active: true | null;
  endedByName: string | null;
  endedAt: string | null;
  endReason: string | null;
}

export interface SampleDelivery {
  id: number;
  trackingNumber: string;
  deliveryType: DeliveryType;
  status: DeliveryStatus;
  source: DeliverySource;
  estimatedFee: number | null;
  specialInstructions: string | null;
  createdAt: string;
  deliveredAt: string | null;
  pickup: DispatchAddress;
  destination: DispatchAddress;
  parcels: DispatchParcel[];
  trackingEvents: DispatchTrackingEvent[];
  courierId: number | null;
}

const now = Date.now();
const hoursAgo = (hours: number) => new Date(now - hours * 3_600_000).toISOString();

function address(contactName: string, contactPhone: string, street: string, city: string, district: string): DispatchAddress {
  return { contactName, contactPhone, street, city, district, postalCode: null };
}

let parcelId = 1;
function parcel(description: string, weightKg: number, size: [number, number, number] = [30, 20, 15]): DispatchParcel {
  const [lengthCm, widthCm, heightCm] = size;
  return { id: parcelId++, description, weightKg, lengthCm, widthCm, heightCm, declaredValue: null };
}

function event(status: DeliveryStatus, description: string, hours: number, locationName: string | null = null): DispatchTrackingEvent {
  return { status, description, locationName, eventTime: hoursAgo(hours) };
}

const colomboWarehouse = address('SwiftDrop Colombo Hub', '0112345678', '45 Baseline Road', 'Colombo', 'Colombo');
const kandyShop = address('Kandy Crafts', '0812233445', '12 Dalada Veediya', 'Kandy', 'Kandy');

export const sampleCouriers: SampleCourier[] = [
  { courierId: 1, name: 'Kasun Fernando', phoneNumber: '0771234567', vehicleType: 'Motorbike', vehicleNumber: 'WP BCD-4521', availability: 'AVAILABLE' },
  { courierId: 2, name: 'Ravi Silva', phoneNumber: '0712345678', vehicleType: 'Van', vehicleNumber: 'CP LK-2290', availability: 'AVAILABLE' },
  { courierId: 3, name: 'Chamara Bandara', phoneNumber: '0759876543', vehicleType: 'Motorbike', vehicleNumber: 'SP BHA-7710', availability: 'BUSY' },
  { courierId: 4, name: 'Nadeesha Herath', phoneNumber: '0764567890', vehicleType: 'Three-wheeler', vehicleNumber: 'WP QK-3318', availability: 'OFFLINE' },
];

export const sampleDeliveries: SampleDelivery[] = [
  {
    id: 25, trackingNumber: 'SD10480025', deliveryType: 'STANDARD', status: 'CONFIRMED', source: 'MERCHANT',
    estimatedFee: 450, specialInstructions: null, createdAt: hoursAgo(1), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Rashmi De Silva', '0771112233', '18 Kandy Road', 'Kurunegala', 'Kurunegala'),
    parcels: [parcel('Phone accessories', 1.1)],
    trackingEvents: [event('PENDING', 'Order received from merchant', 1.1), event('CONFIRMED', 'Booking confirmed', 1)],
    courierId: null,
  },
  {
    id: 21, trackingNumber: 'SD10480021', deliveryType: 'EXPRESS', status: 'CONFIRMED', source: 'MERCHANT',
    estimatedFee: 350, specialInstructions: 'Call before arriving', createdAt: hoursAgo(2.5), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Ishani Perera', '0713334455', '7 Peradeniya Road', 'Kandy', 'Kandy'),
    parcels: [parcel('Cosmetics', 0.7, [20, 15, 10])],
    trackingEvents: [event('PENDING', 'Order received from merchant', 2.6), event('CONFIRMED', 'Booking confirmed', 2.5)],
    courierId: null,
  },
  {
    id: 23, trackingNumber: 'SD10480023', deliveryType: 'STANDARD', status: 'CONFIRMED', source: 'CUSTOMER',
    estimatedFee: 540, specialInstructions: null, createdAt: hoursAgo(3.5), deliveredAt: null,
    pickup: kandyShop, destination: address('Sunil Abeywardena', '0755556677', '3 Lake Road', 'Nuwara Eliya', 'Nuwara Eliya'),
    parcels: [parcel('Handloom textiles', 2.5, [40, 30, 20]), parcel('Wooden carvings', 1.5)],
    trackingEvents: [event('PENDING', 'Delivery booked', 3.6), event('CONFIRMED', 'Booking confirmed', 3.5)],
    courierId: null,
  },
  {
    id: 27, trackingNumber: 'SD10480027', deliveryType: 'STANDARD', status: 'PENDING', source: 'CUSTOMER',
    estimatedFee: 400, specialInstructions: null, createdAt: hoursAgo(0.4), deliveredAt: null,
    pickup: address('Tharindu Jayasinghe', '0778889900', '22 Galle Road', 'Dehiwala', 'Colombo'),
    destination: address('Nimali Wickramasinghe', '0719990011', '9 Temple Lane', 'Galle', 'Galle'),
    parcels: [parcel('Books', 3)],
    trackingEvents: [event('PENDING', 'Delivery booked, waiting for confirmation', 0.4)],
    courierId: null,
  },
  {
    id: 15, trackingNumber: 'SD10480015', deliveryType: 'STANDARD', status: 'PICKED_UP', source: 'CUSTOMER',
    estimatedFee: 550, specialInstructions: null, createdAt: hoursAgo(6), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Asanka Rajapaksha', '0774445566', '51 Hill Street', 'Dehiwala', 'Colombo'),
    parcels: [parcel('Kitchen appliance', 4.2, [45, 35, 30])],
    trackingEvents: [event('PENDING', 'Delivery booked', 6.2), event('CONFIRMED', 'Booking confirmed', 6), event('PICKED_UP', 'Picked up by courier', 2, 'Colombo Hub')],
    courierId: 1,
  },
  {
    id: 31, trackingNumber: 'SD10480031', deliveryType: 'SAME_DAY', status: 'DELIVERED', source: 'MERCHANT',
    estimatedFee: 350, specialInstructions: null, createdAt: hoursAgo(8), deliveredAt: hoursAgo(3),
    pickup: colomboWarehouse, destination: address('Chathura Mendis', '0716667788', '14 Station Road', 'Moratuwa', 'Colombo'),
    parcels: [parcel('Clothing', 0.9)],
    trackingEvents: [
      event('CONFIRMED', 'Booking confirmed', 8), event('PICKED_UP', 'Picked up by courier', 6, 'Colombo Hub'),
      event('OUT_FOR_DELIVERY', 'Out for delivery', 4.5, 'Moratuwa'), event('DELIVERED', 'Delivered to recipient', 3, 'Moratuwa'),
    ],
    // Delivery.courier keeps who carried it; the assignment itself is closed, so it no longer counts as workload
    courierId: 1,
  },
  {
    id: 26, trackingNumber: 'SD10480026', deliveryType: 'STANDARD', status: 'CONFIRMED', source: 'CUSTOMER',
    estimatedFee: 380, specialInstructions: null, createdAt: hoursAgo(4), deliveredAt: null,
    pickup: kandyShop, destination: address('Mahesh Gamage', '0752223344', '5 University Road', 'Peradeniya', 'Kandy'),
    parcels: [parcel('Documents', 0.3, [32, 24, 2])],
    trackingEvents: [event('PENDING', 'Delivery booked', 4.2), event('CONFIRMED', 'Booking confirmed', 4)],
    courierId: 2,
  },
  {
    id: 14, trackingNumber: 'SD10480014', deliveryType: 'EXPRESS', status: 'OUT_FOR_DELIVERY', source: 'MERCHANT',
    estimatedFee: 300, specialInstructions: 'Leave with security if not home', createdAt: hoursAgo(7), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Dilani Fonseka', '0777778899', '88 High Level Road', 'Nugegoda', 'Colombo'),
    parcels: [parcel('Shoes', 1.2, [35, 25, 15])],
    trackingEvents: [
      event('CONFIRMED', 'Booking confirmed', 7), event('PICKED_UP', 'Picked up by courier', 5, 'Colombo Hub'),
      event('OUT_FOR_DELIVERY', 'Out for delivery', 1, 'Nugegoda'),
    ],
    courierId: 1,
  },
  {
    id: 19, trackingNumber: 'SD10480019', deliveryType: 'STANDARD', status: 'IN_TRANSIT', source: 'MERCHANT',
    estimatedFee: 600, specialInstructions: null, createdAt: hoursAgo(20), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Kumari Senanayake', '0713332211', '40 Main Street', 'Negombo', 'Gampaha'),
    parcels: [parcel('Electronics', 2.8, [40, 30, 20])],
    trackingEvents: [
      event('CONFIRMED', 'Booking confirmed', 20), event('PICKED_UP', 'Picked up by courier', 5, 'Colombo Hub'),
      event('IN_TRANSIT', 'On the way to the destination area', 3, 'Ja-Ela'),
    ],
    courierId: 1,
  },
  {
    id: 12, trackingNumber: 'SD10480012', deliveryType: 'STANDARD', status: 'IN_TRANSIT', source: 'CUSTOMER',
    estimatedFee: 420, specialInstructions: null, createdAt: hoursAgo(26), deliveredAt: null,
    pickup: kandyShop, destination: address('Ruwan Dissanayake', '0756665544', '2 Market Street', 'Matale', 'Matale'),
    parcels: [parcel('Spices', 1.8)],
    trackingEvents: [
      event('CONFIRMED', 'Booking confirmed', 26), event('PICKED_UP', 'Picked up by courier', 6, 'Kandy'),
      event('IN_TRANSIT', 'On the way to the destination area', 4, 'Akurana'),
    ],
    courierId: 2,
  },
  {
    id: 20, trackingNumber: 'SD10480020', deliveryType: 'EXPRESS', status: 'OUT_FOR_DELIVERY', source: 'MERCHANT',
    estimatedFee: 480, specialInstructions: null, createdAt: hoursAgo(9), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Pradeep Kumara', '0718887766', '17 Beach Road', 'Matara', 'Matara'),
    parcels: [parcel('Watch', 0.4, [15, 10, 8])],
    trackingEvents: [
      event('CONFIRMED', 'Booking confirmed', 9), event('PICKED_UP', 'Picked up by courier', 6, 'Colombo Hub'),
      event('OUT_FOR_DELIVERY', 'Out for delivery', 1.5, 'Matara'),
    ],
    courierId: 3,
  },
  {
    id: 18, trackingNumber: 'SD10480018', deliveryType: 'STANDARD', status: 'FAILED', source: 'CUSTOMER',
    estimatedFee: 390, specialInstructions: null, createdAt: hoursAgo(28), deliveredAt: null,
    pickup: kandyShop, destination: address('Anoma Gunasekara', '0772221100', '102 Hill Street', 'Kandy', 'Kandy'),
    parcels: [parcel('Gift box', 1)],
    trackingEvents: [
      event('CONFIRMED', 'Booking confirmed', 28), event('PICKED_UP', 'Picked up by courier', 5, 'Kandy'),
      event('OUT_FOR_DELIVERY', 'Out for delivery', 3, 'Kandy'),
      event('FAILED', 'Address could not be located: house number not found on Hill Street, phone unanswered', 1, 'Kandy'),
    ],
    courierId: 2,
  },
  {
    id: 13, trackingNumber: 'SD10480013', deliveryType: 'STANDARD', status: 'FAILED', source: 'MERCHANT',
    estimatedFee: 360, specialInstructions: null, createdAt: hoursAgo(30), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Lahiru Ekanayake', '0759990088', '6 Church Road', 'Galle', 'Galle'),
    parcels: [parcel('Laptop bag', 1.4, [45, 35, 10])],
    trackingEvents: [
      event('CONFIRMED', 'Booking confirmed', 30), event('PICKED_UP', 'Picked up by courier', 8, 'Colombo Hub'),
      event('OUT_FOR_DELIVERY', 'Out for delivery', 5, 'Galle'), event('FAILED', 'Recipient unavailable', 4.5, 'Galle'),
    ],
    courierId: 3,
  },
  {
    id: 30, trackingNumber: 'SD10480030', deliveryType: 'STANDARD', status: 'FAILED', source: 'CUSTOMER',
    estimatedFee: 410, specialInstructions: null, createdAt: hoursAgo(96), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Saman Rathnayake', '0714443322', '25 Temple Road', 'Hambantota', 'Hambantota'),
    parcels: [parcel('Home decor', 2.2)],
    trackingEvents: [
      event('CONFIRMED', 'Booking confirmed', 96), event('PICKED_UP', 'Picked up by courier', 80, 'Colombo Hub'),
      event('OUT_FOR_DELIVERY', 'Out for delivery', 74, 'Hambantota'), event('FAILED', 'Recipient refused the parcel', 72, 'Hambantota'),
    ],
    courierId: null,
  },
  {
    id: 9, trackingNumber: 'SD10480009', deliveryType: 'STANDARD', status: 'CANCELLED', source: 'CUSTOMER',
    estimatedFee: 330, specialInstructions: null, createdAt: hoursAgo(50), deliveredAt: null,
    pickup: colomboWarehouse, destination: address('Hasini Peiris', '0778880011', '11 Park Avenue', 'Panadura', 'Kalutara'),
    parcels: [parcel('Toys', 1.6)],
    trackingEvents: [event('CONFIRMED', 'Booking confirmed', 50), event('CANCELLED', 'Cancelled by customer', 48)],
    courierId: null,
  },
];

/** Active rows for every assigned delivery, plus closed history rows showing earlier reassignments. */
export const sampleAssignments: SampleAssignment[] = [
  { assignmentId: 1, deliveryId: 15, courierId: 1, assignedByName: 'Dispatch team', assignedAt: hoursAgo(5.5), active: true, endedByName: null, endedAt: null, endReason: null },
  { assignmentId: 2, deliveryId: 31, courierId: 1, assignedByName: 'Dispatch team', assignedAt: hoursAgo(7), active: null, endedByName: 'System', endedAt: hoursAgo(3), endReason: 'Delivered' },
  { assignmentId: 3, deliveryId: 26, courierId: 2, assignedByName: 'Dispatch team', assignedAt: hoursAgo(3.5), active: true, endedByName: null, endedAt: null, endReason: null },
  { assignmentId: 4, deliveryId: 14, courierId: 1, assignedByName: 'Dispatch team', assignedAt: hoursAgo(6), active: true, endedByName: null, endedAt: null, endReason: null },
  { assignmentId: 5, deliveryId: 19, courierId: 3, assignedByName: 'Dispatch team', assignedAt: hoursAgo(19), active: null, endedByName: 'Dispatch team', endedAt: hoursAgo(6), endReason: 'Courier reported a vehicle breakdown' },
  { assignmentId: 6, deliveryId: 19, courierId: 1, assignedByName: 'Dispatch team', assignedAt: hoursAgo(6), active: true, endedByName: null, endedAt: null, endReason: null },
  { assignmentId: 7, deliveryId: 12, courierId: 2, assignedByName: 'Dispatch team', assignedAt: hoursAgo(7), active: true, endedByName: null, endedAt: null, endReason: null },
  { assignmentId: 8, deliveryId: 20, courierId: 3, assignedByName: 'Dispatch team', assignedAt: hoursAgo(7), active: true, endedByName: null, endedAt: null, endReason: null },
  { assignmentId: 9, deliveryId: 18, courierId: 2, assignedByName: 'Dispatch team', assignedAt: hoursAgo(6), active: true, endedByName: null, endedAt: null, endReason: null },
  { assignmentId: 10, deliveryId: 13, courierId: 3, assignedByName: 'Dispatch team', assignedAt: hoursAgo(9), active: true, endedByName: null, endedAt: null, endReason: null },
  { assignmentId: 11, deliveryId: 30, courierId: 3, assignedByName: 'Dispatch team', assignedAt: hoursAgo(82), active: null, endedByName: 'Dispatch team', endedAt: hoursAgo(71), endReason: 'Returned to hub after refusal' },
];

/**
 * Completed deliveries from the last 30 days, for the reports. Some needed a second attempt: their FAILED tracking
 * event stays in the history (a failed attempt) although the delivery itself ended DELIVERED.
 */
const historyRecipients: [string, string, string, string][] = [
  ['Nuwan Perera', '21 Flower Road', 'Colombo', 'Colombo'],
  ['Sachini Fernando', '8 Lake Drive', 'Kandy', 'Kandy'],
  ['Gayan Silva', '33 Beach Road', 'Galle', 'Galle'],
  ['Madushani Kumari', '5 Temple Road', 'Kurunegala', 'Kurunegala'],
  ['Roshan Jayawardena', '70 Main Street', 'Negombo', 'Gampaha'],
  ['Thilini Herath', '14 Station Road', 'Matara', 'Matara'],
];
/** [days ago, deliveries that day, of which needed a second attempt] */
const historyDays: [number, number, number][] = [
  [1, 2, 1], [2, 1, 0], [3, 2, 0], [4, 1, 1], [5, 1, 0], [6, 2, 0], [8, 1, 0], [9, 2, 1], [11, 1, 0], [12, 2, 0],
  [14, 1, 0], [16, 2, 1], [18, 1, 0], [20, 2, 0], [23, 1, 0], [25, 2, 1], [27, 1, 0], [29, 1, 0],
];
let historyId = 100;
for (const [days, count, retried] of historyDays) {
  for (let i = 0; i < count; i += 1) {
    const id = historyId++;
    const [name, street, city, district] = historyRecipients[id % historyRecipients.length];
    const deliveredHours = days * 24 - 3 - i * 2;
    const createdHours = deliveredHours + 30;
    const secondAttempt = i < retried;
    sampleDeliveries.push({
      id, trackingNumber: `SD1047${String(id).padStart(4, '0')}`, deliveryType: 'STANDARD', status: 'DELIVERED',
      source: id % 2 ? 'MERCHANT' : 'CUSTOMER', estimatedFee: 350 + (id % 4) * 50, specialInstructions: null,
      createdAt: hoursAgo(createdHours), deliveredAt: hoursAgo(deliveredHours),
      pickup: id % 3 ? colomboWarehouse : kandyShop,
      destination: address(name, '0770000000', street, city, district),
      parcels: [parcel('Parcel', 1 + (id % 3))],
      trackingEvents: [
        event('CONFIRMED', 'Booking confirmed', createdHours),
        event('PICKED_UP', 'Picked up by courier', createdHours - 6, 'Colombo Hub'),
        ...(secondAttempt ? [event('FAILED', 'Recipient unavailable', deliveredHours + 20, city)] : []),
        event('DELIVERED', 'Delivered to recipient', deliveredHours, city),
      ],
      courierId: (id % 3) + 1,
    });
  }
}
