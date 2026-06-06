/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

import { CubismMatrix44 } from '@framework/math/cubismmatrix44';
import { ACubismMotion } from '@framework/motion/acubismmotion';
import { CubismWebGLOffscreenManager } from '@framework/rendering/cubismoffscreenmanager';

import * as LAppDefine from './lappdefine';
import { LAppModel } from './lappmodel';
import { LAppPal } from './lapppal';
import { LAppSubdelegate } from './lappsubdelegate';
import { pinyin } from 'pinyin-pro';

type MouthVowel = 'a' | 'i' | 'u' | 'e' | 'o' | 'pause';

type MouthCue = {
  vowel: MouthVowel;
  startMs: number;
  endMs: number;
  baseOpen: number;
  form: number;
};

type MouthFrame = {
  t: number;
  open: number;
  form: number;
};

export class LAppLive2DManager {
  private releaseAllModel(): void {
    this._models.length = 0;
  }

  public setOffscreenSize(width: number, height: number): void {
    for (let i = 0; i < this._models.length; i++) {
      const model: LAppModel = this._models[i];
      model?.setRenderTargetSize(width, height);
    }
  }

  public onDrag(x: number, y: number): void {
    const model: LAppModel = this._models[0];
    if (model) {
      model.setDragging(x, y);
    }
  }

  public setLipSyncValue(value: number): void {
    const model: LAppModel = this._models[0];
    if (model) {
      model.setExternalLipSyncValue(value);
    }
  }

  public clearLipSyncValue(): void {
    const model: LAppModel = this._models[0];
    if (model) {
      model.clearExternalLipSync();
    }
  }
  
  private normalizeMouthFrames(frames: any[]): MouthFrame[] {
    if (!Array.isArray(frames)) return [];
  
    return frames
      .map((item) => ({
        t: Number(item?.t ?? 0),
        open: Math.max(0, Math.min(1, Number(item?.open ?? 0))),
        form: Math.max(-1, Math.min(1, Number(item?.form ?? 0)))
      }))
      .filter((item) => Number.isFinite(item.t))
      .sort((a, b) => a.t - b.t);
  }
  
  private getMouthFrameAt(ms: number): MouthFrame | null {
    if (!this._mouthFrames.length) return null;
  
    for (let i = this._mouthFrames.length - 1; i >= 0; i--) {
      if (ms >= this._mouthFrames[i].t) {
        return this._mouthFrames[i];
      }
    }
  
    return this._mouthFrames[0];
  }
  
  private setMouthPose(open: number, form: number): void {
    const model: LAppModel = this._models[0];
    if (model) {
      model.setExternalMouthPose(open, form);
    }
  }
  
  private clearMouthPose(): void {
    const model: LAppModel = this._models[0];
    if (model) {
      model.clearExternalMouthPose();
    }
  }
  
  private getPinyinArray(text: string): string[] {
    const raw = pinyin(text, { toneType: 'none', type: 'array' } as any) as unknown;
  
    if (Array.isArray(raw)) {
      return raw.map((item) => String(item));
    }
  
    return String(raw ?? '')
      .split(/\s+/)
      .map((item) => item.trim())
      .filter(Boolean);
  }
  
  private extractFinal(syllable: string): string {
    const s = syllable.toLowerCase().replace(/[^a-züv]/g, '');
  
    const initials = [
      'zh', 'ch', 'sh',
      'b', 'p', 'm', 'f', 'd', 't', 'n', 'l',
      'g', 'k', 'h', 'j', 'q', 'x',
      'r', 'z', 'c', 's', 'y', 'w'
    ];
  
    for (const ini of initials) {
      if (s.startsWith(ini)) {
        return s.slice(ini.length);
      }
    }
  
    return s;
  }
  
  private finalToVowel(finalStr: string): MouthVowel {
    const f = finalStr.replace('ü', 'v');
  
    const map: Record<string, MouthVowel> = {
      a: 'a', ia: 'a', ua: 'a', ai: 'a', uai: 'a', an: 'a', ian: 'a', uan: 'a', van: 'a',
      ang: 'a', iang: 'a', uang: 'a', ao: 'a', iao: 'a',
  
      o: 'o', uo: 'o', ou: 'o', ong: 'o', iong: 'o',
  
      e: 'e', ie: 'e', ve: 'e', ei: 'e', en: 'e', eng: 'e', er: 'e',
  
      i: 'i', in: 'i', ing: 'i',
  
      u: 'u', v: 'u', iu: 'u', ui: 'u', un: 'u'
    };
  
    const finals = Object.keys(map).sort((a, b) => b.length - a.length);
    for (const key of finals) {
      if (f === key || f.endsWith(key)) {
        return map[key];
      }
    }
  
    if (f.includes('a')) return 'a';
    if (f.includes('o')) return 'o';
    if (f.includes('e')) return 'e';
    if (f.includes('i')) return 'i';
    if (f.includes('u') || f.includes('v')) return 'u';
  
    return 'pause';
  }
  
  private getVowelPose(vowel: MouthVowel): { form: number; baseOpen: number } {
    const FORM_SIGN = 1;
    // 如果发现圆扁方向反了，就改成 -1
  
    const table: Record<MouthVowel, { form: number; baseOpen: number }> = {
      a: { form: 0.00 * FORM_SIGN, baseOpen: 0.82 },
  
      i: { form: -0.72 * FORM_SIGN, baseOpen: 0.24 },
      e: { form: -0.42 * FORM_SIGN, baseOpen: 0.36 },
  
      u: { form: 0.72 * FORM_SIGN, baseOpen: 0.14 },
      o: { form: 0.50 * FORM_SIGN, baseOpen: 0.26 },
  
      pause: { form: 0, baseOpen: 0 }
    };
  
    return table[vowel];
  }
  
  private buildMouthTimeline(text: string, durationMs: number): MouthCue[] {
    const segments = text
      .split(/([，。！？；、,.!?;:])/)
      .map((s) => s.trim())
      .filter(Boolean);
  
    const tokens: Array<{ vowel: MouthVowel; weight: number }> = [];
  
    for (const seg of segments) {
      if (/^[，。！？；、,.!?;:]+$/.test(seg)) {
        const weight = /[。！？.!?]/.test(seg) ? 0.9 : 0.45;
        tokens.push({ vowel: 'pause', weight });
        continue;
      }
  
      const syllables = this.getPinyinArray(seg);
      for (const syllable of syllables) {
        const finalStr = this.extractFinal(syllable);
        const vowel = this.finalToVowel(finalStr);
        tokens.push({ vowel, weight: 1.0 });
      }
    }
  
    if (!tokens.length) return [];
  
    const totalWeight = tokens.reduce((sum, item) => sum + item.weight, 0);
    let cursor = 0;
    const timeline: MouthCue[] = [];
  
    for (const token of tokens) {
      const span = (token.weight / totalWeight) * durationMs;
      const pose = this.getVowelPose(token.vowel);
  
      timeline.push({
        vowel: token.vowel,
        startMs: cursor,
        endMs: cursor + span,
        baseOpen: pose.baseOpen,
        form: pose.form
      });
  
      cursor += span;
    }
  
    return timeline;
  }
  
  private getCueAt(ms: number): MouthCue | null {
    if (!this._mouthTimeline.length) return null;
  
    for (const cue of this._mouthTimeline) {
      if (ms >= cue.startMs && ms < cue.endMs) {
        return cue;
      }
    }
  
    return this._mouthTimeline[this._mouthTimeline.length - 1];
  }

  private getAudio(): HTMLAudioElement {
    if (!this._audio) {
      this._audio = new Audio();
      this._audio.preload = 'auto';
      this._audio.crossOrigin = 'anonymous';
    }
    return this._audio;
  }
  
  private postAudioStatus(status: string): void {
	console.log('[APP] postAudioStatus:', status)
    window.parent?.postMessage(
      {
        source: 'live2d-status',
        type: 'audio-status',
        status
      },
      window.location.origin
    )
  }

  private ensureAudioGraph(): void {
    if (this._audioContext && this._analyser && this._audioSourceNode) return;

    const audio = this.getAudio();
    const AudioCtx =
      window.AudioContext ||
      (window as typeof window & { webkitAudioContext?: typeof AudioContext })
        .webkitAudioContext;

    if (!AudioCtx) {
      console.error('[APP] AudioContext not supported');
      return;
    }

    this._audioContext = new AudioCtx();
    this._audioSourceNode = this._audioContext.createMediaElementSource(audio);
    this._analyser = this._audioContext.createAnalyser();
    this._analyser.fftSize = 256;
    this._audioDataArray = new Uint8Array(this._analyser.frequencyBinCount);

    this._audioSourceNode.connect(this._analyser);
    this._analyser.connect(this._audioContext.destination);
  }

  public unlockAudio(): void {
    if (this._audioUnlocked) return;

    this.ensureAudioGraph();
    if (!this._audioContext) return;

    this._audioContext
      .resume()
      .then(() => {
        this._audioUnlocked = true;
        console.log('[APP] audio unlocked');
      })
      .catch((err) => {
        console.warn('[APP] audio unlock failed', err);
      });
  }

  public playAudioUrl(
    url: string,
    text: string = '',
    mouthFrames: MouthFrame[] = []
  ): void {
    const audio = this.getAudio();
  
    this._speechText = text || '';
    this._mouthFrames = this.normalizeMouthFrames(mouthFrames);
    this._useMouthFrames = this._mouthFrames.length > 0;
  
    this._mouthTimeline = [];
    this._mouthTimelineReady = false;
    this._mouthOpenSmooth = 0;
    this._mouthFormSmooth = 0;
  
    this.ensureAudioGraph();
  
    const finalUrl = new URL(url, window.location.href).toString();
  
    audio.pause();
    audio.src = finalUrl;
    audio.currentTime = 0;
  
    audio.onloadedmetadata = () => {
      const durationMs = Math.max(0, (audio.duration || 0) * 1000);
  
      if (!this._useMouthFrames) {
        this._mouthTimeline = this.buildMouthTimeline(this._speechText, durationMs);
        this._mouthTimelineReady = true;
      }
    };
  
    audio.onloadeddata = () => {
      this.postAudioStatus('loading');
    };
  
    audio.onplay = () => {
      this.postAudioStatus('playing');
    };
  
    audio.onplaying = () => {
      this.postAudioStatus('playing');
    };
  
    audio.onended = () => {
      this.postAudioStatus('ended');
      this.clearMouthPose();
      this.clearLipSyncValue();
    };
  
    audio.onerror = () => {
      this.postAudioStatus('error');
      this.clearMouthPose();
    };
  
    audio.play()
      .then(() => {
        console.log('[APP] playing audio:', finalUrl);
        this.postAudioStatus('playing');
        this.startLipSyncFromAudio();
      })
      .catch((err) => {
        console.error('[APP] audio play failed', err);
        this.postAudioStatus('error');
        this.clearMouthPose();
      });
  }

  public playLocalAudio(url: string): void {
    this.playAudioUrl(url);
  }
  
  public stopAudio(): void {
    if (this._lipSyncLoopId !== null) {
      cancelAnimationFrame(this._lipSyncLoopId)
      this._lipSyncLoopId = null
    }
  
    this.clearLipSyncValue()
  
    if (this._audio) {
      this._audio.pause()
      this._audio.currentTime = 0
    }
  
    this.postAudioStatus('ended')
  }

  private startLipSyncFromAudio(): void {
    if (!this._analyser || !this._audioDataArray) return;
  
    if (this._lipSyncLoopId !== null) {
      cancelAnimationFrame(this._lipSyncLoopId);
      this._lipSyncLoopId = null;
    }
  
    const NOISE_GATE = 0.03;
    const GAIN = 5.4;
    const MAX_OPEN = 0.76;
    const OPEN_ATTACK = 0.20;
    const OPEN_RELEASE = 0.08;
    const FORM_SMOOTH = 0.18;
    const MIN_CLOSE = 0.03;
  
    const loop = () => {
      if (!this._analyser || !this._audioDataArray || !this._audio) return;
  
      this._analyser.getByteTimeDomainData(this._audioDataArray as any);
  
      let sum = 0;
      for (let i = 0; i < this._audioDataArray.length; i++) {
        const x = (this._audioDataArray[i] - 128) / 128;
        sum += x * x;
      }
  
      const rms = Math.sqrt(sum / this._audioDataArray.length);
      const amp = rms < NOISE_GATE ? 0 : Math.max(0, Math.min(1, rms * GAIN));
  
      const nowMs = this._audio.currentTime * 1000;
  
      let targetOpen = 0;
      let targetForm = 0;
  
      if (this._useMouthFrames) {
        const frame = this.getMouthFrameAt(nowMs);
  
        if (frame) {
          targetOpen = Math.min(MAX_OPEN, frame.open * 0.88);
          targetForm = frame.form * 0.82;
        }
      } else {
        const cue = this._mouthTimelineReady ? this.getCueAt(nowMs) : null;
  
        if (cue && cue.vowel !== 'pause') {
          targetOpen = Math.min(MAX_OPEN, cue.baseOpen * (0.26 + amp * 0.55));
          targetForm = cue.form * 0.82;
        } else {
          targetOpen = Math.min(MAX_OPEN, amp * 0.45);
          targetForm = 0;
        }
      }
  
      if (targetOpen > this._mouthOpenSmooth) {
        this._mouthOpenSmooth += (targetOpen - this._mouthOpenSmooth) * OPEN_ATTACK;
      } else {
        this._mouthOpenSmooth += (targetOpen - this._mouthOpenSmooth) * OPEN_RELEASE;
      }
  
      this._mouthFormSmooth += (targetForm - this._mouthFormSmooth) * FORM_SMOOTH;
  
      if (this._mouthOpenSmooth < MIN_CLOSE) {
        this._mouthOpenSmooth = 0;
      }
  
      if (Math.abs(this._mouthFormSmooth) < 0.025) {
        this._mouthFormSmooth = 0;
      }
  
      this.setMouthPose(this._mouthOpenSmooth, this._mouthFormSmooth);
  
      if (!this._audio.paused && !this._audio.ended) {
        this._lipSyncLoopId = requestAnimationFrame(loop);
      } else {
        this.clearMouthPose();
        this.clearLipSyncValue();
        this._lipSyncLoopId = null;
      }
    };
  
    this._lipSyncLoopId = requestAnimationFrame(loop);
  }

  public connectLipSyncSocket(url: string): void {
    this._lipSyncSocketUrl = url;

    if (this._lipSyncWs) {
      this._lipSyncWs.close();
      this._lipSyncWs = null;
    }

    this._lipSyncWs = new WebSocket(url);

    this._lipSyncWs.onopen = () => {
      console.log('[APP] lipSync websocket connected');
    
      window.parent?.postMessage(
        {
          source: 'live2d-status',
          type: 'socket-status',
          connected: true
        },
        window.location.origin
      )
    
      if (this._reconnectTimer !== null) {
        window.clearTimeout(this._reconnectTimer);
        this._reconnectTimer = null;
      }
    };

    this._lipSyncWs.onmessage = (event) => {
      try {
        console.log('[APP] websocket raw message:', event.data);

        const data = JSON.parse(event.data);

        if (
          (data.type === 'audio' && typeof data.url === 'string') ||
          typeof data.audioUrl === 'string'
        ) {
          const url =
            typeof data.audioUrl === 'string' ? data.audioUrl : data.url;
          this.playAudioUrl(url);
        }

        let raw =
          typeof data.value === 'number'
            ? data.value
            : typeof data.volume === 'number'
            ? data.volume
            : 0;

        if (raw > 1) {
          raw = raw / 100;
        }

        raw = Math.max(0, Math.min(1, raw));

        console.log('[APP] lipSync value:', raw);
        this.setLipSyncValue(raw);
      } catch (err) {
        console.error('[APP] lipSync websocket parse error', err);
      }
    };

    this._lipSyncWs.onerror = (err) => {
      console.error('[APP] lipSync websocket error', err);
    };

    this._lipSyncWs.onclose = () => {
      console.log('[APP] lipSync websocket closed');
    
      window.parent?.postMessage(
        {
          source: 'live2d-status',
          type: 'socket-status',
          connected: false
        },
        window.location.origin
      );
    
      if (this._reconnectTimer === null && this._lipSyncSocketUrl) {
        this._reconnectTimer = window.setTimeout(() => {
          this._reconnectTimer = null;
          this.connectLipSyncSocket(this._lipSyncSocketUrl);
        }, 1500);
      }
    };
  }
  
  public disconnectLipSyncSocket(): void {
    if (this._lipSyncWs) {
      this._lipSyncWs.close();
      this._lipSyncWs = null;
    }
  }

  public onTap(x: number, y: number): void {
    console.log('[APP] onTap fired');
  
    this.unlockAudio();
    this.playAudioUrl('/static/live2d/audio/test.mp3');
  
    if (LAppDefine.DebugLogEnable) {
      LAppPal.printMessage(
        `[APP]tap point: {x: ${x.toFixed(2)} y: ${y.toFixed(2)}}`
      );
    }
  
    const model: LAppModel = this._models[0];
    if (!model) return;
  
    if (model.hitTest(LAppDefine.HitAreaNameHead, x, y)) {
      if (LAppDefine.DebugLogEnable) {
        LAppPal.printMessage(`[APP]hit area: [${LAppDefine.HitAreaNameHead}]`);
      }
      model.setRandomExpression();
    } else if (model.hitTest(LAppDefine.HitAreaNameBody, x, y)) {
      if (LAppDefine.DebugLogEnable) {
        LAppPal.printMessage(`[APP]hit area: [${LAppDefine.HitAreaNameBody}]`);
      }
      model.startRandomMotion(
        LAppDefine.MotionGroupTapBody,
        LAppDefine.PriorityNormal,
        this.finishedMotion,
        this.beganMotion
      );
    }
  }

  public onUpdate(): void {
    const gl = this._subdelegate.getGl();
    CubismWebGLOffscreenManager.getInstance().beginFrameProcess(gl);

    const { width, height } = this._subdelegate.getCanvas();

    const projection: CubismMatrix44 = new CubismMatrix44();
    const model: LAppModel = this._models[0];
    if (!model) return;

    if (model.getModel()) {
      if (model.getModel().getCanvasWidth() > 1.0 && width < height) {
        model.getModelMatrix().setWidth(2.0);
        projection.scale(1.0, width / height);
      } else {
        projection.scale(height / width, 1.0);
      }

      if (this._viewMatrix != null) {
        projection.multiplyByMatrix(this._viewMatrix);
      }
    }

    model.update();
    model.draw(projection);

    CubismWebGLOffscreenManager.getInstance().endFrameProcess(gl);
    CubismWebGLOffscreenManager.getInstance().releaseStaleRenderTextures(gl);
  }

  public nextScene(): void {
    const no: number = (this._sceneIndex + 1) % LAppDefine.ModelDirSize;
    this.changeScene(no);
  }

  private changeScene(index: number): void {
    this._sceneIndex = index;

    if (LAppDefine.DebugLogEnable) {
      LAppPal.printMessage(`[APP]model index: ${this._sceneIndex}`);
    }

    const model: string = LAppDefine.ModelDir[index];
    const modelPath: string = LAppDefine.ResourcesPath + model + '/';
    const modelJsonName: string = 'haru.model3.json';

    this.releaseAllModel();
    const instance = new LAppModel();
    instance.setSubdelegate(this._subdelegate);
    instance.loadAssets(modelPath, modelJsonName);
    this._models.push(instance);
  }

  public setViewMatrix(m: CubismMatrix44): void {
    for (let i = 0; i < 16; i++) {
      this._viewMatrix.getArray()[i] = m.getArray()[i];
    }
  }

  public addModel(sceneIndex: number = 0): void {
    this._sceneIndex = sceneIndex;
    this.changeScene(this._sceneIndex);
  }

  public constructor() {
    this._subdelegate = null;
    this._viewMatrix = new CubismMatrix44();
    this._models = new Array<LAppModel>();
    this._sceneIndex = 0;

    this._lipSyncWs = null;
    this._lipSyncSocketUrl = '';
    this._reconnectTimer = null;

    this._audio = null;
    this._audioContext = null;
    this._audioSourceNode = null;
    this._analyser = null;
    this._audioDataArray = null;
    this._audioUnlocked = false;
    this._lipSyncLoopId = null;
  }

  public release(): void {
    if (this._lipSyncWs) {
      this._lipSyncWs.close();
      this._lipSyncWs = null;
    }

    if (this._reconnectTimer !== null) {
      window.clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }

    if (this._lipSyncLoopId !== null) {
      cancelAnimationFrame(this._lipSyncLoopId);
      this._lipSyncLoopId = null;
    }

    if (this._audio) {
      this._audio.pause();
      this._audio = null;
    }

    if (this._audioContext) {
      this._audioContext.close();
      this._audioContext = null;
    }
  }

  public initialize(subdelegate: LAppSubdelegate): void {
    this._subdelegate = subdelegate;
    this.changeScene(this._sceneIndex);
  
    window.addEventListener('message', (event) => {
      if (event.origin !== window.location.origin) return
  
      const data = event.data || {}
      if (data.source !== 'uni-live2d-control') return
  
      switch (data.type) {
        case 'play-test-mp3':
          this.unlockAudio()
          this.playLocalAudio('/static/live2d/audio/test.mp3')
          break
  
        case 'stop-audio':
          this.stopAudio()
          break
  
        case 'connect-backend':
          this.connectLipSyncSocket(
            typeof data.url === 'string'
              ? data.url
              : 'ws://127.0.0.1:8080/ws/lipsync'
          )
          break
		
		case 'play-remote-audio':
		  this.unlockAudio()
		  if (typeof data.url === 'string' && data.url) {
		    this.playAudioUrl(
		      data.url,
		      typeof data.text === 'string' ? data.text : '',
		      Array.isArray(data.mouthFrames) ? data.mouthFrames : []
		    )
		  }
		  break
		
		case 'disconnect-backend':
		  this.disconnectLipSyncSocket()
		  break
  
        default:
          break
      }
    });
  }

  private _subdelegate: LAppSubdelegate;
  private _lipSyncWs: WebSocket | null;
  private _lipSyncSocketUrl: string;
  private _reconnectTimer: number | null;

  private _audio: HTMLAudioElement | null;
  private _audioContext: AudioContext | null;
  private _audioSourceNode: MediaElementAudioSourceNode | null;
  private _analyser: AnalyserNode | null;
  private _audioDataArray: Uint8Array | null;
  private _audioUnlocked: boolean;
  private _lipSyncLoopId: number | null;
  private _speechText: string = '';
  private _mouthTimeline: MouthCue[] = [];
  private _mouthTimelineReady: boolean = false;
  private _mouthOpenSmooth: number = 0;
  private _mouthFormSmooth: number = 0;
  private _mouthFrames: MouthFrame[] = [];
  private _useMouthFrames: boolean = false;

  _viewMatrix: CubismMatrix44;
  _models: Array<LAppModel>;
  private _sceneIndex: number;

  beganMotion = (self: ACubismMotion): void => {
    LAppPal.printMessage('Motion Began:');
    console.log(self);
  };

  finishedMotion = (self: ACubismMotion): void => {
    LAppPal.printMessage('Motion Finished:');
    console.log(self);
  };
}