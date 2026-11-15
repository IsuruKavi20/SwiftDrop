import styles from './DeliveryScene.module.css';

/**
 * A small isometric "delivery town" drawn in SVG: a SwiftDrop lorry drives down the road, drops a parcel at a door,
 * a check pops and a "delivered" note slides in. Decorative only (aria-hidden); one 9 s CSS loop, still when the
 * device asks for reduced motion. All shapes use the SwiftDrop palette.
 */
type Point = [number, number];

const COS = 0.866;
const SIN = 0.5;
const along = (n: number): Point => [COS * n, -SIN * n]; // up-right, the road direction
const across = (n: number): Point => [COS * n, SIN * n]; // down-right, towards the viewer
const up = (n: number): Point => [0, -n];
const add = (start: Point, ...moves: Point[]): Point =>
  moves.reduce<Point>((sum, move) => [sum[0] + move[0], sum[1] + move[1]], start);
const points = (...list: Point[]) => list.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');

// The road's centre line: from the lower-left edge of the ground to the upper-right edge
const ROAD_START: Point = [105, 225];
const ROAD_STEP: Point = [190, -110];
const onRoad = (t: number): Point => [ROAD_START[0] + ROAD_STEP[0] * t, ROAD_START[1] + ROAD_STEP[1] * t];
const ROAD_HALF = 18;

interface BoxProps {
  origin: Point;
  length: number;
  width: number;
  height: number;
  tone: 'gold' | 'cream' | 'lorry' | 'cab' | 'box';
}

/** An isometric box: the back-left-bottom corner at `origin`, seen from the front (three faces). */
function IsoBox({ origin, length, width, height, tone }: BoxProps) {
  const top = add(origin, up(height));
  return (
    <g className={styles[tone]}>
      <polygon className={styles.rear} points={points(origin, add(origin, across(width)), add(origin, across(width), up(height)), top)} />
      <polygon className={styles.side} points={points(add(origin, across(width)), add(origin, across(width), along(length)), add(origin, across(width), along(length), up(height)), add(origin, across(width), up(height)))} />
      <polygon className={styles.top} points={points(top, add(top, along(length)), add(top, along(length), across(width)), add(top, across(width)))} />
    </g>
  );
}

/** Lit windows on the front face of a building */
function Windows({ origin, width, length, height }: { origin: Point; width: number; length: number; height: number }) {
  const windows: Point[] = [];
  for (let v = 8; v < height - 6; v += 11) {
    for (let u = 5; u < length - 6; u += 10) windows.push([u, v]);
  }
  const face = add(origin, across(width));
  return (
    <g className={styles.windows}>
      {windows.map(([u, v], index) => (
        <polygon
          key={`${u}-${v}`}
          className={index % 3 === 1 ? styles.twinkle : undefined}
          points={points(add(face, along(u), up(v)), add(face, along(u + 5), up(v)), add(face, along(u + 5), up(v + 6)), add(face, along(u), up(v + 6)))}
        />
      ))}
    </g>
  );
}

interface Building { t: number; length: number; width: number; height: number; tone: 'gold' | 'cream'; far: boolean }

const BUILDINGS: Building[] = [
  { t: 0.82, length: 26, width: 20, height: 54, tone: 'cream', far: true },
  { t: 0.5, length: 34, width: 26, height: 30, tone: 'gold', far: true }, // the house that gets the parcel
  { t: 0.1, length: 30, width: 22, height: 40, tone: 'cream', far: true },
  { t: 0.72, length: 22, width: 18, height: 20, tone: 'gold', far: false },
  { t: 0.28, length: 30, width: 24, height: 26, tone: 'cream', far: false },
];

const buildingOrigin = (b: Building): Point =>
  b.far ? add(onRoad(b.t), across(-(ROAD_HALF + 8 + b.width))) : add(onRoad(b.t), across(ROAD_HALF + 8));

const TREES: Point[] = [add(onRoad(0.02), across(48)), add(onRoad(0.95), across(40)), add(onRoad(0.4), across(-74))];

export function DeliveryScene({ className }: { className?: string }) {
  const truckStart = add(onRoad(-0.25), [-33.8, 9.5]); // puts the lorry's middle on the road
  const roadPoly = points(
    add(onRoad(-0.4), across(-ROAD_HALF)), add(onRoad(1.4), across(-ROAD_HALF)),
    add(onRoad(1.4), across(ROAD_HALF)), add(onRoad(-0.4), across(ROAD_HALF)),
  );
  const far = BUILDINGS.filter((b) => b.far);
  const near = BUILDINGS.filter((b) => !b.far);

  return (
    <div className={`${styles.wrap} ${className ?? ''}`} aria-hidden="true">
      <svg className={styles.svg} viewBox="0 0 400 310" fill="none">
        <defs>
          <clipPath id="sd-ground">
            <polygon points="10,170 200,60 390,170 200,280" />
          </clipPath>
        </defs>

        {/* ground tile with a thick edge */}
        <polygon className={styles.slabLeft} points="10,170 200,280 200,298 10,188" />
        <polygon className={styles.slabRight} points="200,280 390,170 390,188 200,298" />
        <polygon className={styles.ground} points="10,170 200,60 390,170 200,280" />

        <g clipPath="url(#sd-ground)">
          <polygon className={styles.road} points={roadPoly} />
          <line className={styles.roadLine} x1={onRoad(-0.4)[0]} y1={onRoad(-0.4)[1]} x2={onRoad(1.4)[0]} y2={onRoad(1.4)[1]} />

          {far.map((b) => (
            <g key={b.t}>
              <IsoBox origin={buildingOrigin(b)} length={b.length} width={b.width} height={b.height} tone={b.tone} />
              <Windows origin={buildingOrigin(b)} width={b.width} length={b.length} height={b.height} />
            </g>
          ))}

          {/* the lorry: cab first (further away), then the cargo box */}
          <g transform={`translate(${truckStart[0]} ${truckStart[1]})`}>
            <g className={styles.truck}>
              <ellipse className={styles.shadow} cx="34" cy="-3" rx="34" ry="12" transform="rotate(-30 34 -3)" />
              <IsoBox origin={along(44)} length={14} width={20} height={15} tone="cab" />
              <polygon className={styles.windscreen} points={points(add(along(52), across(20), up(7)), add(along(57), across(20), up(7)), add(along(57), across(20), up(13)), add(along(52), across(20), up(13)))} />
              <IsoBox origin={[0, 0]} length={44} width={20} height={22} tone="lorry" />
              <polygon className={styles.stripe} points={points(add(across(20), along(4), up(9)), add(across(20), along(40), up(9)), add(across(20), along(40), up(13)), add(across(20), along(4), up(13)))} />
              {[9, 36, 52].map((u) => {
                const [x, y] = add(across(20), along(u));
                return <ellipse key={u} className={styles.wheel} cx={x} cy={y} rx="4.2" ry="5.4" transform={`rotate(-30 ${x} ${y})`} />;
              })}
            </g>
          </g>

          {near.map((b) => (
            <g key={b.t}>
              <IsoBox origin={buildingOrigin(b)} length={b.length} width={b.width} height={b.height} tone={b.tone} />
            </g>
          ))}

          {TREES.map(([x, y]) => (
            <g key={`${x}-${y}`} className={styles.tree}>
              <ellipse cx={x} cy={y} rx="9" ry="5" className={styles.shadow} />
              <rect x={x - 1.5} y={y - 12} width="3" height="12" rx="1.5" className={styles.trunk} />
              <circle cx={x} cy={y - 17} r="9" className={styles.leaves} />
            </g>
          ))}
        </g>

        {/* the parcel hops from the lorry to the door, then a check pops */}
        <g className={styles.parcel}>
          <IsoBox origin={[-5, 4]} length={9} width={8} height={8} tone="box" />
        </g>
        <g className={styles.check}>
          <circle r="10" className={styles.checkBubble} />
          <path d="M-4.5 0.5 L-1.2 3.8 L5 -3" className={styles.checkMark} />
        </g>
      </svg>

      <div className={styles.toast}>
        <span className={styles.toastIcon}>✓</span>
        <span><strong>Parcel delivered</strong><small>Example · just now</small></span>
      </div>
    </div>
  );
}
