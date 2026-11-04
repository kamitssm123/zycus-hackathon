/** Mirrors com.slotmind.common.dto.ApiError — keep both sides in sync. */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: { field: string; message: string }[];
  path?: string;
  requestId?: string;
  timestamp?: string;
}

export type Specialty = 'GENERAL' | 'SPECIALIST' | 'PEDIATRIC';
export type ProviderStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';
export type SlotStatus = 'OPEN' | 'BOOKED' | 'CANCELLED';
export type AppointmentType = 'ROUTINE' | 'URGENT' | 'FOLLOW_UP';
export type AppointmentStatus = 'CONFIRMED' | 'RESCHEDULE_PENDING' | 'RESCHEDULED' | 'COMPLETED';
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';
export type TriggerReason = 'INITIAL' | 'PROVIDER_OFFLINE' | 'SLOT_CANCELLED';

export interface Provider {
  id: string;
  name: string;
  specialty: Specialty;
  status: ProviderStatus;
}

export interface Slot {
  id: string;
  providerId: string;
  startTime: string;
  durationMinutes: number;
  status: SlotStatus;
}

export interface Appointment {
  id: string;
  patientName: string;
  type: AppointmentType;
  providerId: string;
  slotId: string;
  status: AppointmentStatus;
  updatedAt: string;
}

export interface RescheduleSuggestion {
  id: string;
  appointmentId: string;
  recommendedSlotId: string;
  alternateProviderId?: string;
  confidence: number;
  reasoning: string;
  status: SuggestionStatus;
  triggerReason: TriggerReason;
  strategyKey: string;
  fallbackUsed: boolean;
  fallbackReason?: string;
  createdAt: string;
}

export interface SuggestionDecision {
  suggestionId: string;
  suggestionStatus: SuggestionStatus;
  appointmentId: string;
  appointmentStatus: AppointmentStatus;
  slotId: string;
  providerId: string;
  releasedSlotId?: string;
  siblingsRejected: number;
}

export interface AlternativeSuggestion {
  rejected: SuggestionDecision;
  /** Absent when nothing suitable is left to offer. */
  alternative?: RescheduleSuggestion;
}

export interface SchedulingConfig {
  strategy: string;
  fallbackStrategy: string;
  availableStrategies: string[];
}
