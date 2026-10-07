export type Unit = 'lb' | 'kg';
export type Range = readonly [number, number];
export type Goal = 'hypertrophy' | 'strength' | 'mixed';
export type Experience = 'novice' | 'intermediate' | 'advanced';
export type Confidence = 'low' | 'moderate' | 'high';
export type ProgressionStyle = 'auto' | 'double' | 'dynamic' | 'ladder' | 'linear' | 'wave' | 'e1rm';
export type Weekday = 'monday' | 'tuesday' | 'wednesday' | 'thursday' | 'friday' | 'saturday' | 'sunday';
export interface EngineRequestV1 {
    schemaVersion: 1;
    athlete: { experience: Experience; trainingAgeMonths?: number };
    goal: { type: Goal; musclePriorities?: Record<string, string>; liftPriorities?: Record<string, string> };
    schedule: { days: Array<{ day: Weekday; maxMinutes: number; minMinutes?: number; targetExercises?: number; equipmentOverride?: string[] }> };
    equipment: { available: string[]; bodyweight?: 'allow' | 'exclude'; loading?: { unit?: Unit; [key: string]: unknown } };
    restrictions?: { maxBarbellMovementsPerDay?: number; allowSupersets?: boolean };
    preferences?: { avoidedExercises?: string[]; preferredExercises?: string[]; progressionStyle?: ProgressionStyle; responseCapacityScale?: number; [key: string]: unknown };
    customExercises?: unknown[];
    seed?: number;
    [extension: string]: unknown;
}
export type EngineRequestInput = Omit<EngineRequestV1, 'schemaVersion'> & { schemaVersion?: 1 };
export interface PrescriptionV1 { reps: Range; rir: Range; restSeconds: number }
export interface PlannedExerciseV1 { exerciseId: string; name: string; sets: number; role: string; prescription: PrescriptionV1; progressionStyle?: ProgressionStyle; [extension: string]: unknown }
export interface EngineProgramV1 { schemaVersion: 1; id: string; engineVersion: string; phase: string; sessions: Array<{ id: string; day: Weekday; maxMinutes: number; estimatedMinutes: number; exercises: PlannedExerciseV1[]; [extension: string]: unknown }>; [extension: string]: unknown }
export interface HistoryExposureV1 { schemaVersion: 1; exerciseId: string; completedAt: string; programId?: string; sessionId?: string; unit?: Unit; sets: Array<{ load: number; reps: number; rir: number | null; painFlag?: boolean; techniqueQuality?: string }>; [extension: string]: unknown }
export declare const DOMAIN_SCHEMA_VERSIONS: Readonly<Record<'request' | 'program' | 'prescription' | 'exposure' | 'response', 1>>;
export declare const DOMAIN_SCHEMAS: Readonly<Record<string, { version: 1; required: readonly string[]; legacyVersion: 'unversioned' }>>;
export declare class DomainContractError extends Error { constructor(code: string, path: string, message: string); code: string; path: string }
export declare function migrateDomainRecord<T extends object>(kind: keyof typeof DOMAIN_SCHEMA_VERSIONS, input: T): T & { schemaVersion: 1 };
export declare function validatePrescription(input: unknown, path?: string): PrescriptionV1;
export declare function validateEngineProgram(input: unknown): EngineProgramV1;
export declare function validateHistoryExposure(input: unknown): HistoryExposureV1;
