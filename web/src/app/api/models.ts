/**
 * Friendly aliases over the types generated from the backends' OpenAPI specs.
 * The backend owns the contract: run `npm run api:types` with both services up
 * to regenerate claims-api.ts / insights-api.ts after an API change.
 */
import type { components as Claims } from './claims-api';
import type { components as Insights } from './insights-api';

type S = Claims['schemas'];

export type ClaimDetail = S['ClaimDetail'];
export type ClaimSummary = S['ClaimSummary'];
export type TimelineEntry = S['TimelineEntry'];
export type InfoRequestView = S['InfoRequestView'];
export type SubmitClaimRequest = S['SubmitClaimRequest'];
export type Actor = S['Actor'];
export type LoginResponse = S['LoginResponse'];
export type DemoUser = S['DemoUser'];

export type ClaimStatus = NonNullable<ClaimDetail['status']>;
export type ClaimAction = NonNullable<ClaimDetail['allowedActions']>[number];
export type Market = SubmitClaimRequest['market'];
export type ClaimType = SubmitClaimRequest['type'];

export type Exposure = Insights['schemas']['Exposure'];
export type Workload = Insights['schemas']['Workload'];

/** RFC 7807 body returned by both services on errors. */
export interface Problem {
  title?: string;
  status?: number;
  detail?: string;
}
