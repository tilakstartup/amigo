import { Agent } from '@strands-agents/sdk'
import { BedrockModel } from '@strands-agents/sdk/bedrock'

Deno.serve(() => {
  return new Response(JSON.stringify({ ok: true, Agent: typeof Agent, BedrockModel: typeof BedrockModel }), {
    headers: { 'Content-Type': 'application/json' }
  })
})
