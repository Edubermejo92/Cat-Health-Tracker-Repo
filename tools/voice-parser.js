/**
 * Interprete de voz del marcador, comun al movil y al reloj.
 *
 * El reloj tiene una copia exacta de esta logica en Kotlin (VoiceParser.kt) y
 * los dos leen la misma gramatica (tools/voice_grammar.json). Si cambias algo
 * aqui, cambialo alli: tools/probar-voz.sh pasa las mismas frases por los dos
 * y falla si no dicen lo mismo.
 *
 * parse(frase, idioma, nombres) devuelve una accion o null:
 *   {type:'point', team}          team: 'A' | 'B' | 'SRV' (saque) | 'RCV' (resto) | null (no se sabe de quien)
 *   {type:'score', a, b}          0..3 = 0, 15, 30, 40; 3-3 son iguales
 *   {type:'adv', team}  {type:'game', team}  {type:'set', team}  {type:'serve', team}
 *   {type:'undo'} {type:'fault'} {type:'doubleFault'} {type:'newMatch'} {type:'query'}
 */
const VoiceParser = {
    g: null, _cache: {},

    init(grammar){ this.g = grammar; this._cache = {}; },

    norm(s){
        return String(s||'')
            .replace(/[٠-٩]/g, d => String(d.charCodeAt(0)-0x0660))
            .replace(/[۰-۹]/g, d => String(d.charCodeAt(0)-0x06F0))
            .normalize('NFKD').replace(/\p{M}+/gu,'').normalize('NFC')
            .toLowerCase().replace(/ß/g,'ss')
            .replace(/[^\p{L}\p{N}]+/gu,' ').trim();
    },

    lang(code){
        if(this._cache[code]) return this._cache[code];
        const src = this.g.langs[code] || this.g.langs.en;
        const out = { sub: !!src.sub };
        Object.keys(src).forEach(k => {
            if(Array.isArray(src[k])) out[k] = src[k].map(x=>this.norm(x)).filter(Boolean);
        });
        return (this._cache[code] = out);
    },

    /** Donde aparece la frase: [inicio, fin] en t. Sin espacios (chino...) vale dentro de palabra. */
    find(t, p, sub){
        const res = [];
        if(!p) return res;
        if(sub){
            let i = t.indexOf(p);
            while(i >= 0){ res.push([i, i+p.length]); i = t.indexOf(p, i+1); }
        }else{
            const T = ' '+t+' ', P = ' '+p+' ';
            let i = T.indexOf(P);
            while(i >= 0){ res.push([i, i+p.length]); i = T.indexOf(P, i+1); }
        }
        return res;
    },

    has(t, list, sub){ return (list||[]).some(p => this.find(t, p, sub).length > 0); },

    /** Puntos dichos en orden: 0, 15, 30, 40 en palabras del idioma o en cifras. */
    numbers(t, G){
        const hits = [];
        ['zero','fifteen','thirty','forty'].forEach((k, v) => {
            (G[k]||[]).forEach(p => this.find(t, p, G.sub).forEach(([s,e]) => hits.push([s,e,v])));
        });
        const re = /\d+/g; let m;
        const val = {'0':0,'00':0,'15':1,'30':2,'40':3};
        while((m = re.exec(t))){ if(m[0] in val) hits.push([m.index, m.index+m[0].length, val[m[0]]]); }
        hits.sort((x,y) => x[0]-y[0] || (y[1]-y[0])-(x[1]-x[0]));
        const out = []; let end = -1;
        hits.forEach(h => { if(h[0] >= end){ out.push(h[2]); end = h[1]; } });
        return out;
    },

    side(t, G, names, letters){
        const sub = G.sub, min = sub ? 2 : 3;
        const byName = list => (list||[]).some(n => { const x = this.norm(n); return x.length >= min && this.find(t, x, sub).length > 0; });
        let a = this.has(t, G.teamA, sub) || byName(names && names.A);
        let b = this.has(t, G.teamB, sub) || byName(names && names.B);
        if(!a && !b && letters && !sub){
            const toks = t.split(' ');
            const last = toks[toks.length-1];
            if(toks.length >= 2){
                if(this.g.letterA.includes(last)) a = true;
                else if(this.g.letterB.includes(last)) b = true;
            }
        }
        return (a && !b) ? 'A' : (b && !a) ? 'B' : null;
    },

    parse(text, code, names){
        if(!this.g) return null;
        const G = this.lang(code), sub = G.sub;
        const t = this.norm(text);
        if(!t) return null;
        const h = list => this.has(t, list, sub);
        const kw = h(G.point) || h(G.game) || h(G.set) || h(G.advantage) || h(G.serveChange);

        if(h(G.undo)) return {type:'undo'};
        if(h(G.newMatch)) return {type:'newMatch'};
        if(h(G.query)) return {type:'query'};
        if(h(G.doubleFault)) return {type:'doubleFault'};
        if(h(G.fault)) return {type:'fault'};
        if(h(G.serveChange)) return {type:'serve', team: this.side(t, G, names, true)};
        if(h(G.advantage)){
            const team = this.side(t, G, names, true) || (h(G.receiver) ? 'RCV' : h(G.server) ? 'SRV' : null);
            return {type:'adv', team};
        }
        const n = this.numbers(t, G);
        if(n.length >= 2) return {type:'score', a:n[0], b:n[1]};
        if(n.length === 1 && h(G.all)) return {type:'score', a:n[0], b:n[0]};
        if(n.length === 0 && h(G.deuce)) return {type:'score', a:3, b:3};
        if(h(G.game)) return {type:'game', team: this.side(t, G, names, true)};
        if(h(G.set)) return {type:'set', team: this.side(t, G, names, true)};
        const team = this.side(t, G, names, kw);
        if(team) return {type:'point', team};
        if(h(G.receiver)) return {type:'point', team:'RCV'};
        if(h(G.server)) return {type:'point', team:'SRV'};
        if(h(G.point)) return {type:'point', team:null};
        return null;
    }
};
if(typeof module !== 'undefined') module.exports = VoiceParser;
