export type DomainKind = 'request' | 'program' | 'prescription' | 'workout' | 'adaptation' | 'cycle';
export type Unit = 'lb' | 'kg';
export type Owner = 'engine' | 'user';
export type Pair = [number, number];
export interface Request {
    athlete: { experience: 'novice' | 'intermediate' | 'advanced'; [key: string]: unknown };
    goal: { type: 'strength' | 'hypertrophy' | 'mixed'; [key: string]: unknown };
    schedule: { days: { day: string; minMinutes: number; maxMinutes: number; targetExercises?: number }[] };
    equipment: { available: string[]; bodyweight: 'allow' | 'exclude'; loading?: { unit: Unit; [key: string]: unknown } };
    preferences?: Record<string, unknown>; restrictions?: Record<string, unknown>; seed?: number;
    [key: string]: unknown;
}
export interface PlannedExercise {
    exerciseId: string; name: string; sets: number; role: string;
    prescription: { reps: Pair; rir: Pair; restSeconds: number };
    progressionStyle?: string; advancedTechnique?: { type: string; note?: string };
    [key: string]: unknown;
}
export interface Program {
    sessions: { day: string; exercises: PlannedExercise[]; [key: string]: unknown }[];
    audit?: { result: string; findings: unknown[] }; [key: string]: unknown;
}
export interface Prescription {
    exerciseId: string; sets: number; reps?: string | Pair; rir?: string | Pair;
    ownership?: Record<string, Owner>; [key: string]: unknown;
}
export interface Workout {
    id: string; programId: string; dayId?: string; unit?: Unit;
    perf: Record<string, { sets: Record<string, unknown>[]; [key: string]: unknown }>;
    [key: string]: unknown;
}
export interface Adaptation { decisions: Record<string, unknown>[]; [key: string]: unknown }
export interface Cycle { phase: string; workoutsInPhase: number; [key: string]: unknown }
export interface DomainValues { request: Request; program: Program; prescription: Prescription; workout: Workout; adaptation: Adaptation; cycle: Cycle }
export interface DomainRecord<K extends DomainKind> { schemaVersion: 1; kind: K; value: DomainValues[K] }
export const DOMAIN_SCHEMA_VERSION: 1;
export const DOMAIN_RECORD_KINDS: readonly DomainKind[];
export function validateDomainValue<K extends DomainKind>(kind: K, value: unknown): DomainValues[K];
export function migrateDomainRecord<K extends DomainKind>(kind: K, input: unknown): DomainRecord<K>;
export function readDomainRecord<K extends DomainKind>(kind: K, input: unknown): DomainValues[K];
