'use server';
/**
 * @fileOverview A traffic re-route summarization AI agent.
 *
 * - summarizeTrafficReRoutes - A function that handles the traffic re-route summarization process.
 * - SummarizeTrafficReRoutesInput - The input type for the summarizeTrafficReRoutes function.
 * - SummarizeTrafficReRoutesOutput - The return type for the summarizeTrafficReRoutes function.
 */

import {ai} from '@/ai/genkit';
import {z} from 'genkit';

const SummarizeTrafficReRoutesInputSchema = z.object({
  congestionData: z
    .string()
    .describe('Live congestion data for the affected area.'),
});
export type SummarizeTrafficReRoutesInput = z.infer<typeof SummarizeTrafficReRoutesInputSchema>;

const SummarizeTrafficReRoutesOutputSchema = z.object({
  reRouteSuggestions: z
    .string()
    .describe('Gemini-summarized re-route suggestions based on the congestion data.'),
});
export type SummarizeTrafficReRoutesOutput = z.infer<typeof SummarizeTrafficReRoutesOutputSchema>;

export async function summarizeTrafficReRoutes(
  input: SummarizeTrafficReRoutesInput
): Promise<SummarizeTrafficReRoutesOutput> {
  return summarizeTrafficReRoutesFlow(input);
}

const prompt = ai.definePrompt({
  name: 'summarizeTrafficReRoutesPrompt',
  input: {schema: SummarizeTrafficReRoutesInputSchema},
  output: {schema: SummarizeTrafficReRoutesOutputSchema},
  prompt: `You are an AI assistant for traffic controllers. Your task is to provide Gemini-summarized re-route suggestions based on live congestion data.

  Here is the live congestion data:
  {{congestionData}}

  Provide clear and concise re-route suggestions that drivers can easily follow to alleviate traffic.`,
});

const summarizeTrafficReRoutesFlow = ai.defineFlow(
  {
    name: 'summarizeTrafficReRoutesFlow',
    inputSchema: SummarizeTrafficReRoutesInputSchema,
    outputSchema: SummarizeTrafficReRoutesOutputSchema,
  },
  async input => {
    const {output} = await prompt(input);
    return output!;
  }
);
